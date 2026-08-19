package com.ooooyt.babycommander.ui.tui;

import com.ooooyt.babycommander.ui.UiEvent;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link TuiEventInterpreter} tool-call message handling.
 *
 * <p>Previously {@code TOOL_CALL_START} messages were created with a
 * {@code null} style, which caused {@link TuiRenderer} to route them through
 * {@link MarkdownRenderer#render} (full markdown parsing + emoji stripping)
 * instead of the plain {@link MarkdownRenderer#wrapLine} path used by the
 * other tool events. This garbled tool inputs containing markdown characters
 * (e.g. underscores in {@code write_file} paths were dropped as italics, and
 * backticks/emoji removed). Tool call messages must always be shown verbatim.
 */
class TuiEventInterpreterTest {

    private TuiModel model;
    private TuiEventInterpreter interpreter;

    @BeforeEach
    void setUp() {
        model = new TuiModel();
        interpreter = new TuiEventInterpreter(model, true);
    }

    @Test
    void toolCallPairs_hiddenByDefault_whileDescriptiveTextAndThinkingRemain() {
        // Default interpreter (showToolCallPairs = false) must hide the actual
        // tool-call pair lines (execution + result) but keep the descriptive
        // text and thinking-duration lines visible.
        TuiEventInterpreter def = new TuiEventInterpreter(model);

        // Descriptive text must still render.
        JsonObject textEvent = new JsonObject()
                .put("eventType", "TOOL_CALL_TEXT")
                .put("text", "Let me search the codebase.")
                .put("afterThinking", false);
        def.handleStatusEvent(textEvent);

        // Thinking duration must still render.
        JsonObject thinkEvent = new JsonObject()
                .put("eventType", "THINKING_DURATION")
                .put("thinkingMs", 1200L);
        def.handleStatusEvent(thinkEvent);

        // The pair (execution + result) must be hidden.
        JsonObject startEvent = new JsonObject()
                .put("eventType", "TOOL_CALL_START")
                .put("toolName", "search")
                .put("toolInput", "foo");
        def.handleStatusEvent(startEvent);

        JsonObject resultEvent = new JsonObject()
                .put("eventType", "TOOL_CALL_RESULT")
                .put("toolName", "search")
                .put("toolInput", "foo")
                .put("durationMs", 5L);
        def.handleStatusEvent(resultEvent);

        synchronized (model.chatMessages) {
            // Only descriptive text and thinking-duration lines are present.
            assertEquals(2, model.chatMessages.size(),
                "Tool-call pairs must be hidden by default; text + thinking remain");
            assertEquals("Let me search the codebase.",
                model.chatMessages.get(0).source());
            assertTrue(model.chatMessages.get(1).source().contains("thought in"),
                "Thinking-duration line must remain visible");
        }
    }

    private void handleToolStart(String toolName, String toolInput) {
        JsonObject event = new JsonObject()
                .put("eventType", "TOOL_CALL_START")
                .put("toolName", toolName)
                .put("toolInput", toolInput);
        interpreter.handleStatusEvent(event);
    }

    @Test
    void toolCallStart_getsNonNullStyle_routesThroughPlainWrap() {
        handleToolStart("write_file", "path/to/foo_bar.md");

        synchronized (model.chatMessages) {
            assertEquals(1, model.chatMessages.size());
            ChatMessage msg = model.chatMessages.get(0);
            // Non-null style -> TuiRenderer uses wrapLine (verbatim) not markdown render.
            assertNotNull(msg.style(), "TOOL_CALL_START must carry a style so it renders via wrapLine");
        }
    }

    @Test
    void toolCallStart_preservesMarkdownCharactersInInput() {
        handleToolStart("write_file", "src/main/java/com/example/My_Class.java");

        synchronized (model.chatMessages) {
            ChatMessage msg = model.chatMessages.get(0);
            // The rendered line must keep underscores/backticks verbatim.
            assertTrue(msg.rendered().contains("My_Class.java"),
                    "Underscores in tool input must be preserved verbatim, got: " + msg.rendered());
        }
    }

    @Test
    void toolCallResult_andError_alsoCarryNonNullStyles() {
        JsonObject result = new JsonObject()
                .put("eventType", "TOOL_CALL_RESULT")
                .put("toolName", "read_file")
                .put("toolInput", "a/b")
                .put("durationMs", 5L);
        interpreter.handleStatusEvent(result);

        JsonObject error = new JsonObject()
                .put("eventType", "TOOL_CALL_ERROR")
                .put("toolName", "shell")
                .put("toolInput", "ls")
                .put("errorMessage", "boom");
        interpreter.handleStatusEvent(error);

        synchronized (model.chatMessages) {
            assertEquals(2, model.chatMessages.size());
            assertNotNull(model.chatMessages.get(0).style(), "TOOL_CALL_RESULT must carry a style");
            assertNotNull(model.chatMessages.get(1).style(), "TOOL_CALL_ERROR must carry a style");
        }
    }

    @Test
    void toolCallText_withToolCalls_isRenderedInline() {
        // An assistant message that requests tool calls carries descriptive text
        // which must be printed directly (inline), not buffered.
        JsonObject textEvent = new JsonObject()
                .put("eventType", "TOOL_CALL_TEXT")
                .put("text", "Let me search the codebase for that symbol.")
                .put("afterThinking", false);
        interpreter.handleStatusEvent(textEvent);

        synchronized (model.chatMessages) {
            assertEquals(1, model.chatMessages.size(),
                "Tool-call descriptive text must be rendered inline");
            assertEquals("Let me search the codebase for that symbol.",
                model.chatMessages.get(0).source());
        }
    }

    @Test
    void toolCallText_afterThinking_isBufferedAndFlushedAfterThoughtDuration() {
        // A TOOL_CALL_TEXT event flagged afterThinking=true must NOT render
        // inline; it is buffered until the final response arrives.
        JsonObject textEvent = new JsonObject()
                .put("eventType", "TOOL_CALL_TEXT")
                .put("text", "I reasoned step by step before answering.")
                .put("afterThinking", true);
        interpreter.handleStatusEvent(textEvent);

        synchronized (model.chatMessages) {
            assertEquals(0, model.chatMessages.size(),
                "afterThinking TOOL_CALL_TEXT must be buffered, not rendered inline");
        }

        // When the final response arrives, the answer content comes first, then
        // the buffered text, and the total duration line is printed last.
        UiEvent.MessageOutput finalMsg = new UiEvent.MessageOutput(
            "Here is the answer.", UiEvent.MessageType.MARKDOWN, 2500L);
        interpreter.handleUiEvent(finalMsg);

        synchronized (model.chatMessages) {
            assertEquals(3, model.chatMessages.size());
            assertEquals("Here is the answer.", model.chatMessages.get(0).source());
            assertEquals("I reasoned step by step before answering.", model.chatMessages.get(1).source());
            assertTrue(model.chatMessages.get(2).source().contains("done in"),
                "Last line must be the total duration, got: " + model.chatMessages.get(2).source());
        }
    }

    @Test
    void toolCallText_afterThinking_isFlushedOnlyOnce() {
        JsonObject textEvent = new JsonObject()
                .put("eventType", "TOOL_CALL_TEXT")
                .put("text", "First, let me look around.")
                .put("afterThinking", true);
        interpreter.handleStatusEvent(textEvent);

        UiEvent.MessageOutput finalMsg = new UiEvent.MessageOutput(
            "Done.", UiEvent.MessageType.MARKDOWN, 1000L);
        interpreter.handleUiEvent(finalMsg);

        synchronized (model.chatMessages) {
            assertEquals(3, model.chatMessages.size(), "Text should be flushed exactly once");
        }
    }

    @Test
    void thinkingDuration_isRenderedInlinePerRoundTrip() {
        // A THINKING_DURATION event is rendered inline immediately when it
        // arrives (per round-trip), before the final response is delivered.
        JsonObject thinking = new JsonObject()
                .put("eventType", "THINKING_DURATION")
                .put("thinkingMs", 1200L);
        interpreter.handleStatusEvent(thinking);

        synchronized (model.chatMessages) {
            assertEquals(1, model.chatMessages.size(),
                "THINKING_DURATION must render inline immediately");
            assertTrue(model.chatMessages.get(0).source().contains("thought in"),
                "Line must be the real thinking duration, got: " + model.chatMessages.get(0).source());
        }

        // When the final response arrives, the answer content is added, and the
        // total duration line is printed last.
        UiEvent.MessageOutput finalMsg = new UiEvent.MessageOutput(
            "Done.", UiEvent.MessageType.MARKDOWN, 5000L);
        interpreter.handleUiEvent(finalMsg);

        synchronized (model.chatMessages) {
            assertEquals(3, model.chatMessages.size());
            assertTrue(model.chatMessages.get(0).source().contains("thought in"),
                "First line must be the real thinking duration, got: " + model.chatMessages.get(0).source());
            assertEquals("Done.", model.chatMessages.get(1).source());
            assertTrue(model.chatMessages.get(2).source().contains("done in"),
                "Last line must be the total duration, got: " + model.chatMessages.get(2).source());
        }
    }

    @Test
    void toolCallPairs_hiddenByDefault() {
        // When showToolCallPairs is false (the runtime default), tool calling
        // pairs (execution + result lines) must NOT be printed to the user.
        TuiEventInterpreter hidden = new TuiEventInterpreter(model);

        JsonObject start = new JsonObject()
                .put("eventType", "TOOL_CALL_START")
                .put("toolName", "read_file")
                .put("toolInput", "a/b");
        hidden.handleStatusEvent(start);

        JsonObject result = new JsonObject()
                .put("eventType", "TOOL_CALL_RESULT")
                .put("toolName", "read_file")
                .put("toolInput", "a/b")
                .put("durationMs", 5L);
        hidden.handleStatusEvent(result);

        synchronized (model.chatMessages) {
            assertEquals(0, model.chatMessages.size(),
                "Tool calling pairs must be hidden when showToolCallPairs is false");
        }
    }
}
