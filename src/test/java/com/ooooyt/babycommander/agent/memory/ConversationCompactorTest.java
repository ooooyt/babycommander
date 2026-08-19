package com.ooooyt.babycommander.agent.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationCompactorTest {

    @Test
    void testCompactPassesThroughSummaryMessage() {
        SummaryMessage sm = new SummaryMessage("test summary");
        List<ChatMessage> result = new ConversationCompactor().compact(List.of(sm));
        assertEquals(1, result.size());
        assertSame(sm, result.get(0));
    }

    @Test
    void testCompactPassesThroughAlreadySummarizedAiMessage() {
        AiMessage summarizedAi = AiMessage.from("[summarized] some summary");
        List<ChatMessage> result = new ConversationCompactor().compact(List.of(summarizedAi));
        assertEquals(1, result.size());
        assertSame(summarizedAi, result.get(0));
    }

    @Test
    void testCompactPassesThroughAlreadySummarizedUserMessage() {
        UserMessage summarizedUm = new UserMessage("[summarized] user text summary");
        List<ChatMessage> result = new ConversationCompactor().compact(List.of(summarizedUm));
        assertEquals(1, result.size());
        assertSame(summarizedUm, result.get(0));
    }

    @Test
    void testCompactKeepsSingleUserMessage() {
        UserMessage um = new UserMessage("hello world");
        List<ChatMessage> result = new ConversationCompactor().compact(List.of(um));
        assertEquals(1, result.size());
        assertSame(um, result.get(0));
    }

    @Test
    void testCompactPreservesAlreadySummarizedAmongRawMessages() {
        UserMessage um = new UserMessage("hi");
        AiMessage summarizedAi = AiMessage.from("[summarized] previous summary");
        List<ChatMessage> result = new ConversationCompactor().compact(List.of(um, summarizedAi));
        assertEquals(2, result.size());
        assertSame(um, result.get(0));
        assertSame(summarizedAi, result.get(1));
    }

    @Test
    void testCompactMemorySkipsWhenOnlyOneNonSummaryMessage() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setTaskSummary("Test task");
        memory.add(AiMessage.from("only message"));

        assertEquals(2, memory.messages().size());

        ConversationCompactor compactor = new ConversationCompactor();
        compactor.compactMemory(memory);

        assertEquals(2, memory.messages().size(), "Should not change when only 1 non-summary message");
    }

    @Test
    void testCompactMemorySkipsWhenNoNonSummaryMessages() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setTaskSummary("Test task");

        assertEquals(1, memory.messages().size());

        ConversationCompactor compactor = new ConversationCompactor();
        compactor.compactMemory(memory);

        assertEquals(1, memory.messages().size(), "Should not change when no non-summary messages");
    }

    @Test
    void testMultipleUserMessagesWithAlreadySummarizedDoesNotTriggerLlm() {
        UserMessage um1 = new UserMessage("first");
        AiMessage summarized = AiMessage.from("[summarized] already done");
        UserMessage um2 = new UserMessage("second");

        List<ChatMessage> result = new ConversationCompactor().compact(List.of(um1, summarized, um2));
        assertEquals(3, result.size());
        assertSame(um1, result.get(0));
        assertSame(summarized, result.get(1));
        assertSame(um2, result.get(2));
    }

    @Test
    void testMixedSummaryAndUserMessages() {
        SummaryMessage taskSummary = new SummaryMessage("## Task\ntest");
        UserMessage um = new UserMessage("hello");
        AiMessage alreadyDone = AiMessage.from("[summarized] done");

        List<ChatMessage> result = new ConversationCompactor().compact(List.of(taskSummary, um, alreadyDone));
        assertEquals(3, result.size());
        assertSame(taskSummary, result.get(0));
        assertSame(um, result.get(1));
        assertSame(alreadyDone, result.get(2));
    }

    @Test
    void testBoundaryKeepsRecentUserAndLastAiIntact() {
        UserMessage um1 = new UserMessage("old question");
        AiMessage ai1 = AiMessage.from("old answer");
        ToolExecutionRequest req = ToolExecutionRequest.builder()
            .id("call_1").name("tool1").arguments("{}").build();
        AiMessage ai1b = AiMessage.from("old tool response", List.of(req));
        ToolExecutionResultMessage tr1 = ToolExecutionResultMessage.from(req, "old result");
        UserMessage um2 = new UserMessage("recent question");
        AiMessage ai2 = AiMessage.from("recent answer");

        List<ChatMessage> messages = List.of(um1, ai1, ai1b, tr1, um2, ai2);
        ConversationCompactor compactor = new ConversationCompactor();
        List<ChatMessage> result = compactor.compact(messages);

        assertTrue(result.size() >= 2, "Should have at least recent user + recent AI");
        assertSame(um2, result.get(result.size() - 2), "Recent user message should be preserved");
        assertSame(ai2, result.get(result.size() - 1), "Last AI response should be preserved");
    }

    @Test
    void testBoundaryWithSingleAiPreservesAll() {
        UserMessage um = new UserMessage("question");
        AiMessage ai = AiMessage.from("answer");

        List<ChatMessage> result = new ConversationCompactor().compact(List.of(um, ai));
        assertEquals(2, result.size());
        assertSame(um, result.get(0));
        assertSame(ai, result.get(1));
    }

    @Test
    void testBoundaryWithMultipleExchangesCompactsOldPart() {
        UserMessage um1 = new UserMessage("first");
        AiMessage ai1 = AiMessage.from("first answer");
        UserMessage um2 = new UserMessage("second");
        AiMessage ai2 = AiMessage.from("second answer");
        AiMessage ai3 = AiMessage.from("third answer");

        List<ChatMessage> messages = List.of(um1, ai1, um2, ai2, ai3);
        ConversationCompactor compactor = new ConversationCompactor();
        List<ChatMessage> result = compactor.compact(messages);

        assertSame(ai2, result.get(result.size() - 2), "2nd-to-last AI should be preserved in suffix");
        assertSame(ai3, result.get(result.size() - 1), "Last AI should be preserved in suffix");
    }

    /**
     * Reproduces the root cause of the "insufficient tool messages following tool_calls"
     * API error. When compaction fires mid-tool-loop (the last AiMessage carries
     * tool_execution_requests whose ToolExecutionResultMessages have NOT been added
     * yet — exactly the state at ToolCallAwareChatMemory.add(AiMessage) triggered by
     * onMemoryFull), findBoundary() always keeps the last AiMessage (boundary <=
     * lastAiIdx), so the dangling AiMessage(tool_calls) survives compaction. The
     * resulting message list is then sent to the OpenAI/DeepSeek API which rejects it.
     */
    @Test
    void testCompactMustNotLeaveDanglingToolCallsWhenResultsPending() {
        ToolExecutionRequest req1 = ToolExecutionRequest.builder()
            .id("call_1").name("tool1").arguments("{}").build();
        ToolExecutionRequest req2 = ToolExecutionRequest.builder()
            .id("call_2").name("tool2").arguments("{}").build();

        UserMessage um = new UserMessage("do the work");
        AiMessage ai1 = AiMessage.from("calling tool1", List.of(req1));
        ToolExecutionResultMessage tr1 = ToolExecutionResultMessage.from(req1, "result1");
        // Last message: AiMessage with tool_calls but NO matching ToolExecutionResultMessage
        // (mid-loop state when compaction is triggered by onMemoryFull in add(AiMessage))
        AiMessage ai2 = AiMessage.from("calling tool2", List.of(req2));

        List<ChatMessage> messages = List.of(um, ai1, tr1, ai2);
        List<ChatMessage> result = new ConversationCompactor().compact(messages);

        // After compaction, NO AiMessage in the result may carry tool_execution_requests
        // unless it is immediately followed by ToolExecutionResultMessages for EVERY id.
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                List<String> expectedIds = ai.toolExecutionRequests().stream()
                    .map(r -> r.id()).toList();
                List<String> presentIds = new java.util.ArrayList<>();
                for (int j = i + 1; j < result.size()
                        && presentIds.size() < expectedIds.size(); j++) {
                    if (result.get(j) instanceof ToolExecutionResultMessage trm) {
                        presentIds.add(trm.id());
                    } else {
                        break; // tool results must immediately follow the tool_calls
                    }
                }
                assertEquals(expectedIds.size(), presentIds.size(),
                    "Dangling AiMessage(tool_calls) at index " + i
                        + " is not followed by matching tool results: " + result);
            }
        }
    }

    /**
     * Regression test for the "Invalid assistant message: content or toolcalls must be set"
     * API error. When compaction strips a dangling tool-call AiMessage that has NO text
     * (common with DeepSeek/reasoning models that emit tool calls without content), the code
     * must NOT emit an empty AiMessage.from("") — that is rejected by OpenAI/DeepSeek.
     * Instead the empty assistant message must be removed entirely.
     */
    @Test
    void testStripDanglingToolCallsWithNoTextRemovesMessageEntirely() {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
            .id("call_x").name("toolX").arguments("{}").build();
        // AiMessage with a tool call but NO text (ai.text() == null).
        AiMessage dangling = AiMessage.from(null, List.of(req));

        UserMessage um1 = new UserMessage("first turn");
        AiMessage ai1 = AiMessage.from("first answer");
        UserMessage um2 = new UserMessage("second turn");

        // boundary = max(lastUserIdx=2, secondToLastAiIdx=1) = 2 -> compaction runs,
        // suffix keeps [um2, dangling], and stripDanglingToolCalls must remove dangling.
        List<ChatMessage> messages = List.of(um1, ai1, um2, dangling);
        List<ChatMessage> result = new ConversationCompactor().compact(messages);

        // No AiMessage with empty content nor lingering tool calls should remain.
        for (ChatMessage m : result) {
            if (m instanceof AiMessage ai) {
                assertFalse(ai.hasToolExecutionRequests(),
                    "No AiMessage with tool calls should survive dangling stripping: " + result);
                assertTrue(ai.text() == null || !ai.text().isEmpty(),
                    "No AiMessage with empty content should be emitted: " + result);
            }
        }
    }

    /**
     * Regression test: when stripping a dangling tool-call AiMessage that DOES have text,
     * the text must be preserved (content set, no tool calls) so the message stays valid.
     */
    @Test
    void testStripDanglingToolCallsKeepsText() {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
            .id("call_y").name("toolY").arguments("{}").build();
        AiMessage dangling = AiMessage.from("I'll call a tool now", List.of(req));

        UserMessage um1 = new UserMessage("first turn");
        AiMessage ai1 = AiMessage.from("first answer");
        UserMessage um2 = new UserMessage("second turn");

        List<ChatMessage> messages = List.of(um1, ai1, um2, dangling);
        List<ChatMessage> result = new ConversationCompactor().compact(messages);

        boolean found = false;
        for (ChatMessage m : result) {
            if (m instanceof AiMessage ai && ai.text() != null
                    && ai.text().contains("I'll call a tool now")) {
                found = true;
                assertFalse(ai.hasToolExecutionRequests(), "Dangling tool calls must be stripped");
            }
        }
        assertTrue(found, "The assistant text should be preserved in the result: " + result);
    }

    /**
     * Regression test for the duplicate BackgroundMessage bug. compactMemory() must not
     * feed the BackgroundMessage into compact()/replaceMemoryHistory(), otherwise it ends
     * up BOTH in the backgroundMessage field AND in the messages list, so messages()
     * emits it twice (and more after repeated compactions).
     */
    @Test
    void testCompactMemoryDoesNotDuplicateBackgroundMessage() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setBackground("Workspace background info");
        memory.setTaskSummary("Task brief");

        memory.add(UserMessage.userMessage("q1"));
        memory.add(AiMessage.from("a1"));
        memory.add(UserMessage.userMessage("q2"));
        memory.add(AiMessage.from("a2"));

        assertEquals(1, countBackground(memory.messages()), "Background should appear once before compaction");

        ConversationCompactor compactor = new ConversationCompactor();
        compactor.compactMemory(memory);

        List<ChatMessage> msgs = memory.messages();
        assertEquals(1, countBackground(msgs),
            "Background must appear exactly once after compaction (no duplication)");
    }

    private static int countBackground(List<ChatMessage> msgs) {
        int count = 0;
        for (ChatMessage m : msgs) {
            if (m instanceof BackgroundMessage) count++;
        }
        return count;
    }
}
