package com.ooooyt.babycommander.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Builder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import dev.langchain4j.model.openai.internal.OpenAiUtils;
import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.FunctionCall;
import dev.langchain4j.model.openai.internal.chat.Message;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import dev.langchain4j.model.openai.internal.chat.ToolMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.internal.chat.ToolType;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.internal.shared.Usage;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public class DeepSeekChatModel implements ChatModel {

    private final OpenAiClient client;
    private final int maxRetries;
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;

    private DeepSeekChatModel(OpenAiClient client, int maxRetries, String modelName, Double temperature, Integer maxTokens) {
        this.client = client;
        this.maxRetries = maxRetries;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Builder(builderClassName = "ModelBuilder", builderMethodName = "builder")
    private static DeepSeekChatModel create(String baseUrl, String apiKey, String modelName, Double temperature, Integer maxTokens, Duration timeout) {
        OpenAiClient client = OpenAiClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .connectTimeout(getOrDefault(timeout, Duration.ofSeconds(15)))
                .readTimeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .build();
        return new DeepSeekChatModel(client, 2, modelName, temperature, maxTokens);
    }

    private static <T> T getOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .messages(toMessages(chatRequest.messages()));

        if (chatRequest.toolSpecifications() != null && !chatRequest.toolSpecifications().isEmpty()) {
            requestBuilder.tools(OpenAiUtils.toTools(chatRequest.toolSpecifications(), false));
        }

        ChatCompletionRequest openAiRequest = requestBuilder.build();

        ChatCompletionResponse openAiResponse = client.chatCompletion(openAiRequest).execute();

        AssistantMessage assistant = openAiResponse.choices().get(0).message();
        String text = assistant.content();
        List<ToolExecutionRequest> toolRequests = parseToolCalls(assistant.toolCalls());

        AiMessage.Builder aiBuilder = AiMessage.builder();
        if (!isNullOrBlank(text)) {
            aiBuilder.text(text);
        }
        if (!isNullOrBlank(assistant.reasoningContent())) {
            aiBuilder.thinking(assistant.reasoningContent());
        }
        aiBuilder.toolExecutionRequests(toolRequests);
        AiMessage aiMessage = aiBuilder.build();

        Usage usage = openAiResponse.usage();
        TokenUsage tokenUsage = null;
        if (usage != null) {
            tokenUsage = new TokenUsage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens()
            );
        }

        ChatResponseMetadata metadata = tokenUsage != null
            ? ChatResponseMetadata.builder().tokenUsage(tokenUsage).build()
            : ChatResponseMetadata.builder().build();

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(metadata)
                .build();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return dev.langchain4j.model.chat.request.DefaultChatRequestParameters.EMPTY;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return List.of(new TokenUsageLogger());
    }

    private List<Message> toMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage sm) {
                result.add(dev.langchain4j.model.openai.internal.chat.SystemMessage.from(sm.text()));
            } else if (msg instanceof UserMessage um) {
                result.add(dev.langchain4j.model.openai.internal.chat.UserMessage.from(um.singleText()));
            } else if (msg instanceof ToolExecutionResultMessage tr) {
                result.add(ToolMessage.from(tr.id(), tr.text()));
            } else if (msg instanceof AiMessage ai) {
                result.add(toAssistantMessage(ai));
            } else {
                throw new IllegalArgumentException(I18n.tr(MessageKey.DEEPSEEK_UNKNOWN_MESSAGE_TYPE, msg.getClass().getName()));
            }
        }
        return result;
    }

    private AssistantMessage toAssistantMessage(AiMessage ai) {
        AssistantMessage.Builder builder = AssistantMessage.builder();
        if (!isNullOrBlank(ai.text())) {
            builder.content(ai.text());
        }
        if (!isNullOrBlank(ai.thinking())) {
            builder.reasoningContent(ai.thinking());
        }
        if (ai.hasToolExecutionRequests()) {
            List<ToolCall> toolCalls = ai.toolExecutionRequests().stream()
                    .map(r -> ToolCall.builder()
                            .id(r.id())
                            .type(ToolType.FUNCTION)
                            .function(FunctionCall.builder()
                                    .name(r.name())
                                    .arguments(isNullOrBlank(r.arguments()) ? "{}" : r.arguments())
                                    .build())
                            .build())
                    .toList();
            builder.toolCalls(toolCalls);
        }
        // The API rejects an assistant message that has neither content nor
        // tool_calls. A reasoning-only turn (thinking set, no text, no tool calls)
        // would otherwise produce exactly such an invalid message. Fall back to
        // the thinking text as content so the request stays valid.
        if (!ai.hasToolExecutionRequests() && isNullOrBlank(ai.text()) && !isNullOrBlank(ai.thinking())) {
            builder.content(ai.thinking());
        }
        return builder.build();
    }

    private List<ToolExecutionRequest> parseToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .filter(t -> t.type() == ToolType.FUNCTION)
                .map(t -> ToolExecutionRequest.builder()
                        .id(t.id())
                        .name(t.function().name())
                        .arguments(t.function().arguments())
                        .build())
                .toList();
    }


}
