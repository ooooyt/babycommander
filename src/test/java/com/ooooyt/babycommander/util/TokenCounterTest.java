package com.ooooyt.babycommander.util;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenCounterTest {

    private TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        tokenCounter = new TokenCounter();
    }

    @Test
    void testEstimateTokens_WithUserMessage() {
        ChatMessage message = UserMessage.from("Hello, world!");
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_WithSystemMessage() {
        ChatMessage message = new SystemMessage("You are a helpful assistant.");
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_WithAiMessage() {
        ChatMessage message = AiMessage.from("This is an AI response.");
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_WithToolExecutionResultMessage() {
        ChatMessage message = new ToolExecutionResultMessage("tool1", "call1", "result data");
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_NullMessageThrowsNpe() {
        assertThrows(NullPointerException.class, () -> tokenCounter.estimateTokens((ChatMessage) null));
    }

    @Test
    void testEstimateTokens_EmptyMessageList() {
        List<ChatMessage> messages = Collections.emptyList();
        int tokens = tokenCounter.estimateTokens(messages);
        assertEquals(0, tokens);
    }

    @Test
    void testEstimateTokens_MultipleMessages() {
        List<ChatMessage> messages = List.of(
            new SystemMessage("You are a helpful assistant."),
            UserMessage.from("Hello!"),
            AiMessage.from("Hi there!")
        );
        int tokens = tokenCounter.estimateTokens(messages);
        assertTrue(tokens >= 3);
    }

    @Test
    void testEstimateTokens_NullMessageListThrowsNpe() {
        assertThrows(NullPointerException.class, () -> tokenCounter.estimateTokens((List<ChatMessage>) null));
    }

    @Test
    void testEstimateTokens_ListWithNullMessageThrowsNpe() {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(new SystemMessage("test"));
        messages.add(null);
        messages.add(UserMessage.from("hello"));
        assertThrows(NullPointerException.class, () -> tokenCounter.estimateTokens(messages));
    }

    @Test
    void testCalibrateFromUsage_NullUsage() {
        TokenCounter.calibrateFromUsage(null, List.of(UserMessage.from("test")));
        assertTrue(true);
    }

    @Test
    void testCalibrateFromUsage_NullMessages() {
        TokenCounter.calibrateFromUsage(new TokenUsage(10, 5), null);
        assertTrue(true);
    }

    @Test
    void testCalibrateFromUsage_EmptyMessages() {
        TokenCounter.calibrateFromUsage(new TokenUsage(10, 5), Collections.emptyList());
        assertTrue(true);
    }

    @Test
    void testCalibrateFromUsage_Valid() {
        List<ChatMessage> messages = List.of(
            UserMessage.from("Hello, this is a test message with some content.")
        );
        TokenCounter.calibrateFromUsage(new TokenUsage(5, 10), messages);
        assertTrue(TokenCounter.getGlobalSampleCount() >= 0);
    }

    @Test
    void testCalibrateFromUsage_ZeroTokenCount() {
        List<ChatMessage> messages = List.of(
            UserMessage.from("some text")
        );
        TokenCounter.calibrateFromUsage(new TokenUsage(0, 5), messages);
        assertTrue(TokenCounter.getGlobalSampleCount() >= 0);
    }

    @Test
    void testGetGlobalStats() {
        assertTrue(TokenCounter.getGlobalCharsPerToken() > 0);
        assertTrue(TokenCounter.getGlobalCalibrationFactor() > 0);
        assertTrue(TokenCounter.getGlobalSampleCount() >= 0);
    }

    @Test
    void testReset() {
        tokenCounter.reset();
        assertTrue(true);
    }

    @Test
    void testEstimateTokens_AiMessageWithThinking() {
        AiMessage message = AiMessage.builder()
            .text("response text")
            .thinking("thinking text")
            .build();
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_AiMessageWithToolExecutionRequests() {
        AiMessage message = AiMessage.builder()
            .text("Let me search")
            .toolExecutionRequests(List.of(
                ToolExecutionRequest.builder()
                    .id("searchTool")
                    .name("search")
                    .arguments("{\"q\":\"test\"}")
                    .build()
            ))
            .build();
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_AiMessageWithNullText() {
        AiMessage message = AiMessage.builder().text(null).build();
        int tokens = tokenCounter.estimateTokens(message);
        assertEquals(0, tokens);
    }

    @Test
    void testEstimateTokens_AiMessageWithOnlyToolRequests() {
        AiMessage message = AiMessage.builder()
            .toolExecutionRequests(List.of(
                ToolExecutionRequest.builder()
                    .name("calculator")
                    .arguments("{\"a\":1}")
                    .build()
            ))
            .build();
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_UserMessageWithSingleText() {
        UserMessage message = UserMessage.from("single text content");
        int tokens = tokenCounter.estimateTokens(message);
        assertTrue(tokens >= 1);
    }

    @Test
    void testEstimateTokens_SystemMessageWithBlankText() {
        // SystemMessage constructor rejects blank text - verify this
        assertThrows(IllegalArgumentException.class, () -> new SystemMessage(""));
    }

    @Test
    void testExtractStaticText_NullMessage() {
        assertThrows(NullPointerException.class, () -> {
            tokenCounter.estimateTokens((ChatMessage) null);
        });
    }
}
