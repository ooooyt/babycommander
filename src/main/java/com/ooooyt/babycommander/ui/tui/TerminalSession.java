package com.ooooyt.babycommander.ui.tui;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

/**
 * Owns the terminal lifecycle: alt-screen/raw-mode setup, the WINCH resize
 * handler, the shutdown hook, and the main keystroke read loop.
 *
 * <p>Extracted verbatim from {@code TerminalUIAdapter.start()}. The read loop
 * drives the shared {@link TuiModel} (input buffer, cursor, paste flag,
 * scroll, dirty/last-redraw bookkeeping) and delegates painting to
 * {@link TuiRenderer}, plan refresh to {@link TuiEventInterpreter}, and
 * submitted commands to {@link InputProcessor}. The {@code synchronized(lock)}
 * regions are preserved exactly so the same mutual-exclusion guarantees apply
 * between the read thread, the WINCH handler and the idle redraw path.
 *
 * <p>{@code running} and the {@code stopLatch} live here; the adapter's
 * {@code stop()} delegates to {@link #stop()}.
 */
final class TerminalSession {

    private final TuiModel model;
    private final TuiRenderer renderer;
    private final InputProcessor inputProcessor;
    private final TuiEventInterpreter eventInterpreter;

    private volatile boolean running = false;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    TerminalSession(TuiModel model, TuiRenderer renderer, InputProcessor inputProcessor,
                    TuiEventInterpreter eventInterpreter) {
        this.model = model;
        this.renderer = renderer;
        this.inputProcessor = inputProcessor;
        this.eventInterpreter = eventInterpreter;
    }

    void start() {
        if (running) {
            return;
        }

        try (Terminal terminal = TerminalBuilder.builder()
                .system(true).ffm(true).jni(false).jansi(false).jna(false).build()) {

            model.tw = Math.max(20, terminal.getWidth());
            model.th = Math.max(8, terminal.getHeight());
            PrintWriter pw = terminal.writer();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Use System.out directly since pw may be closed by try-with-resources
                System.out.print("\033[?25h");
                System.out.print("\033[?2004l");
                System.out.print("\033[?1049l");
                System.out.flush();
            }));

            Attributes savedAttrs = terminal.enterRawMode();
            pw.print("\033[?1049h");
            pw.print("\033[?25l");
            pw.print("\033[2J");
            pw.print("\033[?2004h");   // Enable bracket paste mode
            pw.flush();

            TerminalUIAdapter.tuiActive = true;
            running = true;

            final Object lock = new Object();

            terminal.handle(Terminal.Signal.WINCH, sig -> {
                synchronized (lock) {
                    model.tw = Math.max(20, terminal.getWidth());
                    model.th = Math.max(8, terminal.getHeight());
                    try {
                        pw.print("\033[?25l");
                        renderer.fullRedraw(pw);
                        pw.print("\033[?25h");
                    } catch (Exception ignored) {}
                }
            });

            synchronized (lock) {
                renderer.fullRedraw(pw);
            }

            pw.print("\033[?25h");
            pw.flush();
            // Background thread drives the thinking dot animation
            Thread animator = new Thread(() -> {
                while (running) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                    if (model.thinking) {
                        // Atomically advance the active dot so the increment
                        // can't race with a reset from the status thread.
                        int stars = TuiRenderer.thinkingBarStars();
                        int cur = model.dotIndex.get();
                        while (!model.dotIndex.compareAndSet(cur, (cur + 1) % stars)) {
                            cur = model.dotIndex.get();
                        }
                        synchronized (lock) {
                            pw.print("\033[?25l");
                            try { renderer.fullRedraw(pw); } catch (Exception ignored) {}
                        }
                        pw.print("\033[?25h");
                        pw.flush();
                    }
                }
            }, "dot-animator");
            animator.setDaemon(true);
            animator.start();

            while (running) {
                int ch = terminal.reader().read(100);
                if (ch < 0) {
                    if (model.dirty || eventInterpreter.refreshPlanFromTool()) {
                        if (!model.dirty) model.dirty = true;
                        long now = System.currentTimeMillis();
                        if (now - model.lastRedraw < 100) { continue; }
                        model.lastRedraw = now;
                        synchronized (lock) {
                            pw.print("\033[?25l");
                            redrawAndReconcile(pw, true);
                        }
                        pw.print("\033[?25h");
                        pw.flush();
                    }
                    continue;
                }

                synchronized (lock) {
                    boolean redrawDisplay = false;
                    boolean redrawInput   = true;
                    // Snapshot the popup visibility before the keystroke so we
                    // can detect a transition and force a display-region redraw
                    // (the popup lives in the display area, so toggling it on
                    // or off requires repainting that region, not just input).
                    boolean popupWasVisible = model.slashPopupVisible;
                    // Recomputed from the input buffer after each keystroke.
                    model.slashPopupVisible = false;
                    // Set true when a popup selection was just confirmed so
                    // the recompute below does not reopen the popup for the
                    // chosen command name (e.g. "/lang" still starts with '/').
                    boolean popupConfirmed = false;

                    if (ch == 13 || ch == 10 || ch == 9) {
                        if (popupWasVisible && (ch == 9 || ch == 13 || ch == 10)) {
                            // Confirm popup selection: replace the input
                            // buffer with the highlighted command name,
                            // close the popup, and position the cursor at
                            // the end so the user can press Enter again to
                            // submit or keep editing.
                            var matches = SlashCommandRegistry.filter(model.inputBuf.toString());
                            if (!matches.isEmpty()) {
                                int idx = Math.min(model.slashPopupSelectedIndex, matches.size() - 1);
                                String chosen = matches.get(idx).name();
                                model.inputBuf.setLength(0);
                                model.inputBuf.append(chosen);
                                model.cursorPos = chosen.length();
                            }
                            model.slashPopupVisible = false;
                            model.slashPopupSelectedIndex = 0;
                            popupConfirmed = true;
                            redrawDisplay = true;
                            redrawInput = true;
                        } else if (model.pasting) {
                            model.inputBuf.insert(model.cursorPos, ' ');
                            model.cursorPos++;
                        } else {
                            String cmd = model.inputBuf.toString().trim();
                            model.inputBuf.setLength(0);
                            model.cursorPos = 0;
                            if (!cmd.isEmpty()) {
                                inputProcessor.processInput(cmd);
                            }
                            model.chatScroll = Integer.MAX_VALUE;
                            redrawDisplay = true;
                            redrawInput = true;
                        }

                    } else if (ch == 127 || ch == 8) {
                        if (model.cursorPos > 0) {
                            model.inputBuf.deleteCharAt(model.cursorPos - 1);
                            model.cursorPos--;
                        }

                    } else if (ch == 3) {
                        model.inputBuf.setLength(0);
                        model.cursorPos = 0;

                    } else if (ch == 4) {
                        break;

                    } else if (ch == 27) {
                        int c2 = terminal.reader().read(20);
                        if (c2 == '[') {
                            int c3 = terminal.reader().read(20);
                            switch (c3) {
                                case 'A' -> {
                                    if (popupWasVisible) {
                                        int n = SlashCommandRegistry.filter(model.inputBuf.toString()).size();
                                        if (n > 0) {
                                            model.slashPopupSelectedIndex = Math.max(0,
                                                model.slashPopupSelectedIndex - 1);
                                        }
                                        redrawDisplay = true;
                                        redrawInput = false;
                                    } else {
                                        model.chatScroll = Math.max(0, model.chatScroll - 1);
                                        redrawDisplay = true;
                                        redrawInput = false;
                                    }
                                }
                                case 'B' -> {
                                    if (popupWasVisible) {
                                        int n = SlashCommandRegistry.filter(model.inputBuf.toString()).size();
                                        if (n > 0) {
                                            model.slashPopupSelectedIndex = Math.min(n - 1,
                                                model.slashPopupSelectedIndex + 1);
                                        }
                                        redrawDisplay = true;
                                        redrawInput = false;
                                    } else {
                                        model.chatScroll++;
                                        redrawDisplay = true;
                                        redrawInput = false;
                                    }
                                }
                                case '5' -> {
                                    int c4 = terminal.reader().read(20);
                                    if (c4 == '~') {
                                        model.chatScroll += model.bodyH();
                                        redrawDisplay = true;
                                        redrawInput = false;
                                    }
                                }
                                case '6' -> {
                                    int c4 = terminal.reader().read(20);
                                    if (c4 == '~') {
                                        model.chatScroll = Math.max(0, model.chatScroll - model.bodyH());
                                        redrawDisplay = true;
                                        redrawInput = false;
                                    }
                                }
                                case 'C' -> {
                                    model.cursorPos = Math.min(model.inputBuf.length(), model.cursorPos + 1);
                                    redrawInput = true;
                                    redrawDisplay = false;
                                }
                                case 'D' -> {
                                    model.cursorPos = Math.max(0, model.cursorPos - 1);
                                    redrawInput = true;
                                    redrawDisplay = false;
                                }
                                case 'H' -> {
                                    model.cursorPos = 0;
                                    redrawInput = true;
                                    redrawDisplay = false;
                                }
                                case 'F' -> {
                                    model.cursorPos = model.inputBuf.length();
                                    redrawInput = true;
                                    redrawDisplay = false;
                                }
                                case '3' -> {
                                    int c4 = terminal.reader().read(20);
                                    if (c4 == '~' && model.cursorPos < model.inputBuf.length()) {
                                        model.inputBuf.deleteCharAt(model.cursorPos);
                                        redrawInput = true;
                                        redrawDisplay = false;
                                    }
                                }
                                case '2' -> {
                                    int c4 = terminal.reader().read(50);
                                    if (c4 == '0') {
                                        int c5 = terminal.reader().read(20);
                                        if (c5 == '0') {
                                            int c6 = terminal.reader().read(20);
                                            if (c6 == '~') {
                                                model.pasting = true;
                                            }
                                        } else if (c5 == '1') {
                                            int c6 = terminal.reader().read(20);
                                            if (c6 == '~') {
                                                model.pasting = false;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    } else if (ch >= 32) {
                        String s;
                        if (ch > 0xFFFF) {
                            s = new String(Character.toChars(ch));
                        } else {
                            s = String.valueOf((char) ch);
                        }
                        model.inputBuf.insert(model.cursorPos, s);
                        model.cursorPos += s.length();
                    }

                    // Recompute slash-popup visibility from the current input
                    // buffer. Shown when input starts with '/' and at least
                    // one registry command matches the prefix; hidden once
                    // the buffer no longer starts with '/' (after submit,
                    // backspace past '/', or a non-slash character).
                    if (!popupConfirmed) {
                        String bufNow = model.inputBuf.toString();
                        boolean popupNow = bufNow.startsWith("/")
                            && !SlashCommandRegistry.filter(bufNow).isEmpty();
                        model.slashPopupVisible = popupNow;
                        // Clamp the selection index to the current match list
                        // so it stays valid as the filter shrinks/grows.
                        if (popupNow) {
                            int matchCount = SlashCommandRegistry.filter(bufNow).size();
                            model.slashPopupSelectedIndex = Math.min(
                                model.slashPopupSelectedIndex, Math.max(0, matchCount - 1));
                        } else {
                            model.slashPopupSelectedIndex = 0;
                        }
                        // Whenever the popup is or was visible, force a full
                        // display-region redraw so stale popup rows from the
                        // previous (larger) filter result are cleared before
                        // the new (smaller) filtered popup is painted on top.
                        // This also covers the toggle on/off transition.
                        if (popupNow || popupWasVisible) {
                            redrawDisplay = true;
                        }
                    }

                    if (redrawDisplay) {
                        pw.print("\033[?25l");
                        redrawAndReconcile(pw, false);
                    }
                    if (redrawInput && !redrawDisplay) {
                        // Even on an input-only redraw, refresh the popup so
                        // the filtered list tracks each typed character.
                        if (model.slashPopupVisible) {
                            pw.print("\033[?25l");
                            renderer.drawSlashPopup(pw);
                        }
                        pw.print("\033[?25l"); renderer.drawInputLine(pw);
                    }
                }
                pw.print("[?25h");
                pw.flush();
            }
            terminal.setAttributes(savedAttrs);
            pw.print("\033[?25h");
            pw.print("\033[?2004l");
            pw.print("\033[?1049l");
            pw.flush();
        } catch (Exception e) {
            System.out.print("\033[?25h");
            System.out.print("\033[?2004l");
            System.out.print("\033[?1049l");
            System.out.flush();
            throw new RuntimeException("TerminalUIAdapter failed", e);
        } finally {
            TerminalUIAdapter.tuiActive = false;
            System.out.print("\033[?25h");
            System.out.print("\033[?2004l");
            System.out.print("\033[?1049l");
            System.out.flush();
        }
    }

    void stop() {
        running = false;
        stopLatch.countDown();
    }

    void waitUntilStopped() {
        try {
            stopLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Renders the display region and input line, then reconciles the
     * {@code autoScroll}/{@code dirty} flags against the {@link TuiModel#chatVersion}
     * counter sampled immediately before the redraw.
     *
     * <p>This closes the lost-update window responsible for
     * "content generated by {@code /history} is hidden". Messages published by
     * the event-bus consumer (vert.x event loop) while a redraw is in flight
     * &mdash; after the renderer's snapshot but before the loop clears
     * {@code dirty} &mdash; had both of their signals clobbered: the renderer's
     * {@code autoScroll = false} consume overwrote the message's
     * {@code autoScroll = true}, and the loop's {@code dirty = false} overwrote
     * the message's {@code dirty = true}. With nothing left to trigger a
     * follow-up redraw, {@code chatScroll} stayed pinned at the max-scroll of
     * the stale snapshot, leaving the freshly appended lines (footer + last
     * tasks) below the visible area.
     *
     * <p>By sampling {@code chatVersion} before and after the redraw we can
     * detect such mid-render arrivals and re-arm both flags, so the next
     * redraw re-snapshots the now-complete chat log and scrolls to the bottom.
     *
     * @param clearWhenStable {@code true} on the idle path (clear {@code dirty}
     *                        once everything is rendered); {@code false} on
     *                        the keystroke path (leave {@code dirty} untouched,
     *                        only re-arm on change).
     */
    private void redrawAndReconcile(PrintWriter pw, boolean clearWhenStable) throws Exception {
        int versionBefore = model.chatVersion;
        renderer.drawDisplayRegion(pw);
        renderer.drawSlashPopup(pw);
        renderer.drawInputLine(pw);
        if (model.chatVersion != versionBefore) {
            model.autoScroll = true;
            model.dirty = true;
        } else if (clearWhenStable) {
            model.dirty = false;
        }
    }
}
