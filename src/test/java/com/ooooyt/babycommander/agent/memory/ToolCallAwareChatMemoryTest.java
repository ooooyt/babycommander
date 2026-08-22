package com.ooooyt.babycommander.agent.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallAwareChatMemoryTest {

    private static ToolExecutionRequest request(String id) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("read_file")
                .arguments("{\"path\":\"" + id + ".txt\"}")
                .build();
    }

    @Test
    void testInitialMemoryIsEmpty() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        assertEquals(0, memory.messages().size());
        assertEquals(0, memory.countToolCalls());
    }

    @Test
    void testAddNonToolMessage() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(SystemMessage.systemMessage("You are a helpful assistant"));
        assertEquals(1, memory.messages().size());
        assertEquals(0, memory.countToolCalls());
    }

    @Test
    void testAddToolCallPair() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        String toolCallId = "call-1";

        AiMessage aiMsg = AiMessage.aiMessage(List.of(
            ToolExecutionRequest.builder()
                .id(toolCallId)
                .name("read_file")
                .arguments("{\"path\":\"test.txt\"}")
                .build()
        ));
        memory.add(aiMsg);
        memory.add(ToolExecutionResultMessage.from(toolCallId, "read_file", "file content"));

        assertEquals(2, memory.messages().size());
        assertEquals(1, memory.countToolCalls());
    }

    @Test
    void testTrimsOldestToolCallsWhenExceedingMaxToolCalls() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");

        memory.add(SystemMessage.systemMessage("prompt"));
        memory.add(UserMessage.userMessage("do tasks"));

        for (int i = 0; i < 5; i++) {
            String toolCallId = "call-" + i;
            AiMessage aiMsg = AiMessage.aiMessage(List.of(
                ToolExecutionRequest.builder()
                    .id(toolCallId)
                    .name("read_file")
                    .arguments("{\"path\":\"test" + i + ".txt\"}")
                    .build()
            ));
            memory.add(aiMsg);
            memory.add(ToolExecutionResultMessage.from(toolCallId, "read_file", "content-" + i));
        }

        memory.ejectOldMessages(3);
        assertTrue(memory.countToolCalls() <= 3, "Expected at most 3 tool calls but got " + memory.countToolCalls());
    }

    @Test
    void testTrimsOldestToolCallsWhenExceedingMaxMessages() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");

        for (int i = 0; i < 4; i++) {
            String toolCallId = "call-" + i;
            AiMessage aiMsg = AiMessage.aiMessage(List.of(
                ToolExecutionRequest.builder()
                    .id(toolCallId)
                    .name("read_file")
                    .arguments("{\"path\":\"test" + i + ".txt\"}")
                    .build()
            ));
            memory.add(aiMsg);
            memory.add(ToolExecutionResultMessage.from(toolCallId, "read_file", "content-" + i));
        }

        memory.ejectOldMessages(3);
        assertTrue(memory.messages().size() <= 6, "Expected at most 6 messages but got " + memory.messages().size());
    }

    @Test
    void testClearWorks() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(SystemMessage.systemMessage("prompt"));
        memory.add(UserMessage.userMessage("hello"));
        memory.clear();
        assertEquals(0, memory.messages().size());
    }

    @Test
    void testMemoryIdMatches() {
        Object id = "my-session";
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory(id);
        assertEquals(id, memory.id());
    }

    @Test
    void testSummaryIsAddedToMessages() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        SummaryMessage sm = new SummaryMessage("initial summary");
        memory.setSummary(sm);

        assertSame(sm, memory.getSummary());
        assertTrue(memory.messages().contains(sm));
        assertEquals(1, memory.messages().size());
    }

    @Test
    void testSummaryUpdateChangesText() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setSummary(new SummaryMessage("initial"));
        memory.updateSummary("updated summary");

        assertEquals("updated summary", memory.getSummary().text());
        assertEquals("updated summary", ((SystemMessage) memory.messages().get(0)).text());
    }

    @Test
    void testSummarySurvivesTrimByExceedingMaxToolCalls() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");

        memory.setSummary(new SummaryMessage("progress summary"));
        memory.add(SystemMessage.systemMessage("prompt"));
        memory.add(UserMessage.userMessage("do tasks"));

        for (int i = 0; i < 5; i++) {
            String toolCallId = "call-" + i;
            AiMessage aiMsg = AiMessage.aiMessage(List.of(
                ToolExecutionRequest.builder()
                    .id(toolCallId)
                    .name("read_file")
                    .arguments("{\"path\":\"test" + i + ".txt\"}")
                    .build()
            ));
            memory.add(aiMsg);
            memory.add(ToolExecutionResultMessage.from(toolCallId, "read_file", "content-" + i));
        }

        memory.ejectOldMessages(3);
        assertTrue(memory.messages().size() <= 9, "Expected at most 9 messages but got " + memory.messages().size());
    }

    @Test
    void testClearRemovesSummary() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setSummary(new SummaryMessage("progress summary"));
        memory.add(UserMessage.userMessage("hello"));
        memory.clear();

        assertNull(memory.getSummary());
        assertTrue(memory.messages().isEmpty());
    }

    @Test
    void testSummaryIsNotCountedAsToolCall() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setSummary(new SummaryMessage("progress summary"));
        assertEquals(0, memory.countToolCalls());
    }

    @Test
    void testAddViaUpdateSummaryCreatesSummary() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.updateSummary("auto-created summary");

        assertNotNull(memory.getSummary());
        assertEquals("auto-created summary", ((SystemMessage) memory.messages().get(0)).text());
    }

    @Test
    void testSummaryIsAtPositionZero() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(UserMessage.userMessage("user message"));
        memory.setSummary(new SummaryMessage("progress summary"));

        assertSame(memory.getSummary(), memory.messages().get(0));
    }

    @Test
    void testRemovesNonToolMessagesAsFallback() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test", 1);
        memory.add(UserMessage.userMessage("task"));
        memory.add(AiMessage.from("response1"));
        memory.add(AiMessage.from("response2"));
        memory.add(AiMessage.from("response3"));
        memory.add(AiMessage.from("response4"));
        memory.add(AiMessage.from("response5"));

        memory.ejectOldMessages(3);
        assertTrue(memory.messages().size() <= 6, "Expected at most 6 messages but got " + memory.messages().size());
    }

    @Test
    void testUserMessagesAreNotTrimmed() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(UserMessage.userMessage("task1"));
        memory.add(UserMessage.userMessage("task2"));
        memory.add(UserMessage.userMessage("task3"));

        assertEquals(3, memory.messages().size(), "UserMessages should not be trimmed");
    }

    @Test
    void testReplaceMemoryHistoryPreservesThreeSummaries() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setTaskSummary("Fix login bug");
        memory.updatePhaseSummary(List.of(
            Map.of("description", "Analyze", "status", "completed"),
            Map.of("description", "Implement", "status", "active")
        ));
        memory.updateProgressSummary("Working on the fix");

        memory.add(UserMessage.userMessage("initial message"));
        memory.add(AiMessage.from("initial response"));

        ChatMessage summarized = AiMessage.from("summary of all prior work");
        ChatMessage last = AiMessage.from("last response");
        memory.replaceMemoryHistory(summarized, last);

        List<ChatMessage> msgs = memory.messages();
        assertEquals(5, msgs.size(), "Should have 3 summaries + 2 new messages");

        assertInstanceOf(SummaryMessage.class, msgs.get(0));
        assertInstanceOf(SummaryMessage.class, msgs.get(1));
        assertInstanceOf(SummaryMessage.class, msgs.get(2));

        assertEquals("## Task\nFix login bug\n", ((SummaryMessage) msgs.get(0)).text());
        assertTrue(((SummaryMessage) msgs.get(1)).text().contains("Plan Status"));
        assertTrue(((SummaryMessage) msgs.get(2)).text().contains("Working on the fix"));

        assertSame(summarized, msgs.get(3));
        assertSame(last, msgs.get(4));
    }

    @Test
    void testReplaceMemoryHistoryWithZeroMessages() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setTaskSummary("Test");
        memory.updateProgressSummary("Progress");

        memory.replaceMemoryHistory();

        List<ChatMessage> msgs = memory.messages();
        assertEquals(2, msgs.size(), "Should have 2 summaries, no new messages");
        assertTrue(((SystemMessage) msgs.get(0)).text().contains("Task"));
        assertTrue(((SystemMessage) msgs.get(1)).text().contains("Progress"));
    }

    @Test
    void testReplaceMemoryHistoryWithNoSummariesSet() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(UserMessage.userMessage("hello"));

        memory.replaceMemoryHistory(AiMessage.from("replacement"));

        List<ChatMessage> msgs = memory.messages();
        assertEquals(1, msgs.size(), "Should have only the new message");
        assertEquals("replacement", ((AiMessage) msgs.get(0)).text());
    }

    @Test
    void testReplaceMemoryHistoryTokenEstimateIsPositive() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.setTaskSummary("Task");
        memory.add(UserMessage.userMessage("hello"));

        memory.replaceMemoryHistory(AiMessage.from("replacement"));

        assertTrue(memory.getEstimatedTokenTotal() > 0, "Token total should be positive for summaries + new message");
    }

    @Test
    void testRepeatedMessagesCallDoesNotReturnDuplicates() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");

        // Add a system prompt
        memory.add(SystemMessage.systemMessage("You are a helpful assistant"));
        // Add user messages
        memory.add(UserMessage.userMessage("hello"));
        memory.add(UserMessage.userMessage("do task 1"));
        // Add a tool call pair
        String toolCallId = "call-1";
        memory.add(AiMessage.aiMessage(List.of(
            ToolExecutionRequest.builder()
                .id(toolCallId)
                .name("read_file")
                .arguments("{}")
                .build()
        )));
        memory.add(ToolExecutionResultMessage.from(toolCallId, "read_file", "content"));
        // Set summaries
        memory.setSummary(new SummaryMessage("progress"));

        // Call messages() multiple times
        List<ChatMessage> firstCall = memory.messages();
        List<ChatMessage> secondCall = memory.messages();
        List<ChatMessage> thirdCall = memory.messages();

        // Each call should return the same number of messages
        assertEquals(firstCall.size(), secondCall.size(),
            "messages() should return same count on repeated calls");
        assertEquals(secondCall.size(), thirdCall.size(),
            "messages() should return same count on repeated calls");

        // Verify no duplicates within each result (check for duplicate object references)
        for (List<ChatMessage> msgs : List.of(firstCall, secondCall, thirdCall)) {
            assertEquals(msgs.size(), msgs.stream().distinct().count(),
                "messages() should not contain duplicate references: " + msgs);
        }

        // Verify the content is consistent across calls using toString()
        for (int i = 0; i < firstCall.size(); i++) {
            ChatMessage m1 = firstCall.get(i);
            ChatMessage m2 = secondCall.get(i);
            ChatMessage m3 = thirdCall.get(i);
            assertEquals(m1.toString(), m2.toString(),
                "Message at index " + i + " should be equal on repeated calls");
            assertEquals(m2.toString(), m3.toString(),
                "Message at index " + i + " should be equal on repeated calls");
        }
    }

    @Test
    void testMessagesStripsDanglingToolCallWithoutResults() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(UserMessage.userMessage("do task"));
        // Assistant requests a tool call but no ToolExecutionResultMessage follows.
        memory.add(AiMessage.aiMessage(List.of(request("dangling-call"))));

        List<ChatMessage> result = memory.messages();
        // The dangling AiMessage (no text) must be dropped so the API request is valid.
        assertEquals(1, result.size());
        assertEquals(UserMessage.class, result.get(0).getClass());
    }

    @Test
    void testMessagesKeepsTextWhenStrippingDanglingToolCall() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(UserMessage.userMessage("do task"));
        // Assistant message with text AND a dangling tool call (no results).
        AiMessage ai = new AiMessage("Let me search first", List.of(request("dangling-call")));
        memory.add(ai);

        List<ChatMessage> result = memory.messages();
        assertEquals(2, result.size());
        AiMessage last = (AiMessage) result.get(1);
        assertEquals("Let me search first", last.text());
        assertFalse(last.hasToolExecutionRequests(), "dangling tool call should be stripped");
    }

    @Test
    void testMessagesPreservesCompleteToolCallPair() {
        ToolCallAwareChatMemory memory = new ToolCallAwareChatMemory("test");
        memory.add(UserMessage.userMessage("do task"));
        AiMessage ai = AiMessage.aiMessage(List.of(request("call-ok")));
        memory.add(ai);
        memory.add(ToolExecutionResultMessage.from("call-ok", "read_file", "content"));

        List<ChatMessage> result = memory.messages();
        assertEquals(3, result.size());
        assertTrue(((AiMessage) result.get(1)).hasToolExecutionRequests(),
            "complete tool call pair must be preserved");
    }

}
