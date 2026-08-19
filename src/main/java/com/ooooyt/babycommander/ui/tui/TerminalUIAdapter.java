package com.ooooyt.babycommander.ui.tui;

import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.hook.ConfirmationHandler;
import com.ooooyt.babycommander.hook.ConfirmationResult;
import com.ooooyt.babycommander.hook.DangerLevel;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.ui.ChatEngine;
import com.ooooyt.babycommander.ui.CommandEvent;
import com.ooooyt.babycommander.ui.UiAdapter;
import com.ooooyt.babycommander.ui.UiEvent;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.jline.utils.AttributedStyle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Façade implementing {@link UiAdapter} for the terminal UI.
 *
 * <p>Originally a ~1100-line "god class", it now only wires the
 * collaborators together and exposes the {@code UiAdapter} contract:
 * <ul>
 *   <li>{@link TuiModel} &mdash; shared mutable state + geometry</li>
 *   <li>{@link TuiRenderer} &mdash; painting</li>
 *   <li>{@link TuiEventInterpreter} &mdash; event → state translation</li>
 *   <li>{@link InputProcessor} &mdash; prompt input handling</li>
 *   <li>{@link TerminalSession} &mdash; terminal lifecycle + read loop</li>
 * </ul>
 * The public surface (constructor signature, {@link #wire(EventBus,
 * StatusEventPublisher)}, the {@code UiAdapter} methods and the
 * {@link #tuiActive} flag) is unchanged so {@code UiAdapterProducer},
 * {@code CodeGenApp} and {@code ConsoleStatusRenderer} are unaffected.
 */
public class TerminalUIAdapter implements UiAdapter {

    public static volatile boolean tuiActive = false;

    private volatile EventBus eventBus;
    private volatile StatusEventPublisher statusPublisher;

    private final TuiModel model = new TuiModel();
    private final TuiRenderer renderer;
    private final TuiEventInterpreter eventInterpreter;
    private final InputProcessor inputProcessor;
    private final TerminalSession session;

    public TerminalUIAdapter(EventBus eventBus, StatusEventPublisher statusPublisher) {
        this.eventBus = eventBus;
        this.statusPublisher = statusPublisher;
        this.renderer = new TuiRenderer(model);
        boolean showToolCallPairs = new YamlConfigLoader().getConfig().showToolCallPairs;
        this.eventInterpreter = new TuiEventInterpreter(model, showToolCallPairs);
        this.inputProcessor = new InputProcessor(model, eventBus, this::stop);
        this.session = new TerminalSession(model, renderer, inputProcessor, eventInterpreter);
    }

    public void wire(EventBus eb, StatusEventPublisher sp) {
        this.eventBus = eb;
        this.statusPublisher = sp;
        this.inputProcessor.setEventBus(eb);
        if (eb != null) {
            eb.consumer(ChatEngine.UI_EVENT_ADDRESS, message -> {
                if (message.body() instanceof UiEvent event) {
                    eventInterpreter.handleUiEvent(event);
                }
            });
            if (sp != null) {
                sp.registerSyncConsumer(eventInterpreter::handleStatusEvent);
            }
        }
    }

    @Override
    public void start() {
        session.start();
    }

    @Override
    public void stop() {
        session.stop();
        var future = model.pendingConfirmation;
        if (future != null) {
            future.complete(ConfirmationResult.DENY);
        }
    }

    @Override
    public void waitUntilStopped() {
        session.waitUntilStopped();
    }

    @Override
    public void publishCommand(CommandEvent command) {
        if (eventBus != null) {
            eventBus.publish(ChatEngine.UI_COMMAND_ADDRESS, command);
        }
    }

    @Override
    public ConfirmationHandler getConfirmationHandler() {
        return info -> {
            CompletableFuture<ConfirmationResult> future = new CompletableFuture<>();
            model.pendingConfirmation = future;
            model.pendingInfo = info;
            String argsStr = info.argsPreview();
            String allowText = info.level() == DangerLevel.ASK_ONCE
                ? I18n.tr(MessageKey.CONFIRMATION_ALLOW)
                : I18n.tr(MessageKey.CONFIRMATION_ALLOW_SIMPLE);
            // Fixed overhead of the warning line, excluding method name and args:
            //   "[Warn] " + " " (before method) + " → " + "," + " " (before allow) + allowText
            String warnPrefix = I18n.tr(MessageKey.UI_WARN_PREFIX);
            int fixedOverhead = warnPrefix.length() + 1 + " \u2192 ".length() + 1 + 1 + allowText.length();
            // Allow the warning to span up to 2 lines of (rightW - 1) columns each,
            // so the args preview can use the full 2-line display region.
            int maxArgs = Math.max(40, 2 * (model.rightW() - 1) - fixedOverhead - info.methodName().length());
            if (argsStr.length() > maxArgs) {
                argsStr = argsStr.substring(0, maxArgs - 3) + "...";
            }
            String argsPart = argsStr.isEmpty() ? "" : " \u2192 " + argsStr;
            String shortMsg = " " + info.methodName() + argsPart + ",";
            AttributedStyle warnStyle = info.level() == DangerLevel.DANGEROUS
                ? TerminalTheme.ST_DANGER : TerminalTheme.ST_WARN;
            synchronized (model.chatMessages) {
                model.chatMessages.add(new ChatMessage(warnPrefix + shortMsg + " " + allowText,
                    warnPrefix + shortMsg + " " + allowText, warnStyle));
                model.chatVersion++;
            }
            model.autoScroll = true;
            model.dirty = true;
            try {
                long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
                while (System.currentTimeMillis() < deadline) {
                    try {
                        return future.get(100, TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException e) {
                        if (Thread.currentThread().isInterrupted()) {
                            return ConfirmationResult.DENY;
                        }
                    }
                }
                return ConfirmationResult.DENY;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ConfirmationResult.DENY;
            } catch (java.util.concurrent.ExecutionException e) {
                return ConfirmationResult.DENY;
            } finally {
                model.pendingConfirmation = null;
                model.pendingInfo = null;
            }
        };
    }
}
