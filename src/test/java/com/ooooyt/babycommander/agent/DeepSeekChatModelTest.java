package com.ooooyt.babycommander.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekChatModelTest {

    @Test
    @DisplayName("Builder should build model with all fields")
    void testBuilderAllFields() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey("test-api-key")
                .modelName("deepseek-chat")
                .temperature(0.5)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(30))
                .build();

        assertNotNull(model);
    }

    @Test
    @DisplayName("Builder should build model with minimal fields")
    void testBuilderMinimalFields() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey("test-api-key")
                .modelName("deepseek-chat")
                .build();

        assertNotNull(model);
    }

    @Test
    @DisplayName("Builder should chain setters correctly")
    void testBuilderChaining() {
        var builder = DeepSeekChatModel.builder();
        assertSame(builder, builder.baseUrl("url"));
        assertSame(builder, builder.apiKey("key"));
        assertSame(builder, builder.modelName("model"));
        assertSame(builder, builder.temperature(0.5));
        assertSame(builder, builder.maxTokens(1000));
        assertSame(builder, builder.timeout(Duration.ofSeconds(10)));
    }

    @Test
    @DisplayName("defaultRequestParameters should return EMPTY")
    void testDefaultRequestParameters() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey("test-key")
                .modelName("test-model")
                .build();

        assertSame(DefaultChatRequestParameters.EMPTY, model.defaultRequestParameters());
    }

    @Test
    @DisplayName("listeners should return empty list")
    void testListeners() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey("test-key")
                .modelName("test-model")
                .build();

        List<ChatModelListener> listeners = model.listeners();
        assertNotNull(listeners);
        assertFalse(listeners.isEmpty());
        assertTrue(listeners.stream().anyMatch(l ->
                l.getClass().getSimpleName().equals("TokenUsageLogger")));
    }

    @Test
    @DisplayName("doChat should throw exception when API is unreachable")
    void testDoChatWithUnreachableApi() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle system message conversion")
    void testDoChatWithSystemMessage() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("Hello")
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle tool execution result messages")
    void testDoChatWithToolExecutionResult() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        UserMessage.from("What's the weather?"),
                        AiMessage.from(null, List.of(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("getWeather")
                                .arguments("{\"city\": \"Paris\"}")
                                .build())),
                        ToolExecutionResultMessage.from("call-1", "getWeather", "Sunny 25\u00b0C")
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle AiMessage with thinking content")
    void testDoChatWithThinkingContent() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        UserMessage.from("Think step by step"),
                        AiMessage.builder()
                                .text("Final answer")
                                .thinking("I need to think about this...")
                                .build()
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("ChatRequest builder should reject empty messages")
    void testEmptyMessagesRejectedByBuilder() {
        assertThrows(IllegalArgumentException.class, () ->
                ChatRequest.builder()
                        .messages(List.of())
                        .build());
    }

    @Test
    @DisplayName("doChat should handle multiple messages in sequence")
    void testDoChatWithMultipleMessages() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from("Be concise."),
                        UserMessage.from("Hi"),
                        AiMessage.from("Hello! How can I help?"),
                        UserMessage.from("What is Java?")
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle tool specifications in request")
    void testDoChatWithToolSpecifications() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather for a city")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("What's the weather in Paris?")))
                .toolSpecifications(toolSpec)
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle null tool specifications gracefully")
    void testDoChatWithNullToolSpecifications() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle AiMessage with text and tool calls")
    void testDoChatWithAiMessageTextAndToolCalls() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        UserMessage.from("Search and summarize"),
                        AiMessage.builder()
                                .text("Let me search for that.")
                                .toolExecutionRequests(List.of(
                                        ToolExecutionRequest.builder()
                                                .id("call-abc")
                                                .name("search")
                                                .arguments("{\"q\": \"java 21\"}")
                                                .build()
                                ))
                                .build(),
                        ToolExecutionResultMessage.from("call-abc", "search", "Results found")
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle AiMessage with only tool calls (no text)")
    void testDoChatWithAiMessageOnlyToolCalls() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        UserMessage.from("Calculate 2+2"),
                        AiMessage.from(null, List.of(ToolExecutionRequest.builder()
                                .id("call-calc")
                                .name("calculator")
                                .arguments("{\"a\": 2, \"b\": 2}")
                                .build())),
                        ToolExecutionResultMessage.from("call-calc", "calculator", "4")
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle AiMessage with thinking and tool calls")
    void testDoChatWithThinkingAndToolCalls() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        UserMessage.from("Research topic"),
                        AiMessage.builder()
                                .thinking("I should search first...")
                                .toolExecutionRequests(List.of(
                                        ToolExecutionRequest.builder()
                                                .id("call-research")
                                                .name("search")
                                                .arguments("{\"q\": \"topic\"}")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("Multiple tool calls in single AiMessage should be handled")
    void testMultipleToolCallsInAiMessage() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        UserMessage.from("Compare prices"),
                        AiMessage.from(null, List.of(
                                ToolExecutionRequest.builder()
                                        .id("call-1")
                                        .name("getPrice")
                                        .arguments("{\"item\": \"apple\"}")
                                        .build(),
                                ToolExecutionRequest.builder()
                                        .id("call-2")
                                        .name("getPrice")
                                        .arguments("{\"item\": \"banana\"}")
                                        .build()
                        ))
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle messages with special characters in content")
    void testDoChatWithSpecialCharacters() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        UserMessage.from("Line1\nLine2\tTabbed\n\"Quoted\"")
                ))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }

    @Test
    @DisplayName("doChat should handle very long user message")
    void testDoChatWithLongMessage() {
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("test-key")
                .modelName("test-model")
                .timeout(Duration.ofMillis(100))
                .build();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("word ");
        }
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from(sb.toString())))
                .build();

        assertThrows(Exception.class, () -> model.doChat(request));
    }
}
