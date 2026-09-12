package com.ooooyt.babycommander.ui.tui;

import com.ooooyt.babycommander.hook.ConfirmationResult;
import com.ooooyt.babycommander.hook.ToolCallInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable shared state for the terminal UI.
 *
 * <p>Holds the display buffers, terminal geometry, input-editing state and
 * pending confirmation/clarification state that used to live as private
 * fields in {@code TerminalUIAdapter}. The behavioural collaborators
 * ({@link TuiRenderer}, {@link TuiEventInterpreter}, {@link InputProcessor},
 * {@link TerminalSession}) access these package-private fields directly so
 * that the original {@code synchronized} blocks are preserved verbatim &mdash;
 * e.g. {@code synchronized(model.chatMessages)} replaces the former
 * {@code synchronized(chatMessages)} on the identical list instance, keeping
 * the same monitor and therefore the same happens-before guarantees.
 */
final class TuiModel {

    volatile int tw = 80, th = 24;

    final List<ChatMessage> chatMessages = new ArrayList<>();
    final List<InfoLine> infoLines = new ArrayList<>();
    int chatScroll = 0;
    volatile boolean autoScroll = false;
    volatile boolean chatEngineReady = false;

    /**
     * Whether the agent is currently processing a user request (thinking,
     * calling tools, generating a project, etc.). While true the input box is
     * disabled so commands such as {@code /exit} cannot interrupt an
     * in-flight task. Driven by {@link com.ooooyt.babycommander.ui.UiEvent.SessionState#BUSY}
     * and {@code READY_FOR_INPUT} events. Input stays enabled while a
     * confirmation or clarification is pending because the agent is blocked
     * waiting for the user's answer.
     */
    volatile boolean agentBusy = false;

    /**
     * Whether the LLM is currently "thinking" (a request has been submitted
     * but the final thought/response has not yet been rendered). When true,
     * the left panel's second line shows the animated in-progress dot bar.
     */
    volatile boolean thinking = false;

    /**
     * Index of the star currently highlighted orange during thinking. Advanced
     * by the animation thread (wrapping through all the thinking-bar stars)
     * while {@link #thinking} is true.
     */
    final AtomicInteger dotIndex = new AtomicInteger(0);

    volatile boolean dirty = false;
    volatile long lastRedraw = 0;

    /**
     * Monotonically incremented under the {@code chatMessages} monitor every
     * time the chat log is mutated from a non-tui thread (event-bus consumer,
     * status publisher, confirmation handler). The redraw loop samples it
     * before/after a redraw so it can detect content that arrived <em>during</em>
     * rendering and re-arm {@code autoScroll}/{@code dirty} for a follow-up
     * redraw &mdash; closing the lost-update window where the volatile flags
     * alone were clobbered (see {@code HistoryScrollRaceTest}).
     */
    volatile int chatVersion = 0;

    final StringBuilder inputBuf = new StringBuilder();
    int cursorPos = 0;
    volatile boolean pasting = false;

    /**
     * Whether the slash-command popup is currently shown. Set by the read
     * loop whenever {@link #inputBuf} starts with {@code '/'} and there is
     * at least one matching command in {@link SlashCommandRegistry}; cleared
     * otherwise. The renderer reads this to decide whether to paint the
     * popup overlay above the input box.
     */
    volatile boolean slashPopupVisible = false;

    /**
     * Zero-based index of the currently highlighted command within the
     * filtered list shown in the slash-command popup. Moved by UP/DOWN
     * arrow keys when {@link #slashPopupVisible} is true; clamped to the
     * valid range of the current match list. The renderer highlights the
     * row at this index.
     */
    volatile int slashPopupSelectedIndex = 0;

    volatile CompletableFuture<ConfirmationResult> pendingConfirmation = null;
    volatile ToolCallInfo pendingInfo = null;
    volatile CompletableFuture<String> pendingClarification = null;
    volatile UUID pendingClarificationId = null;
    volatile String pendingClarificationQuestion = null;
    volatile List<String> pendingClarificationOptions = null;
    volatile boolean pendingClarificationAllowFree = true;

    int leftW()  { return Math.max(8, tw * 20 / 100); }
    int rightW() { return Math.max(8, tw - leftW()); }
    int bodyH()  { return Math.max(1, th - 6); }
    int inputRow()  { return Math.max(1, th - 2); }
}
