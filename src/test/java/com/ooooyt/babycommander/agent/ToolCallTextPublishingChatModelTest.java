package com.ooooyt.babycommander.agent;

import com.ooooyt.babycommander.status.StatusEventContext;
import com.ooooyt.babycommander.status.StatusEventPublisher;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolCallTextPublishingChatModelTest {

    @Mock
    ChatModel delegate;

    @Mock
    StatusEventPublisher publisher;

    private StatusEventContext context;

    @BeforeEach
    void setUp() {
        context = new StatusEventContext(publisher);
        context.activate();
    }

    @AfterEach
    void tearDown() {
        context.deactivate();
    }

    private ChatRequest request() {
        return ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .build();
    }

    @Test
    @DisplayName("Publishes fallback text when AI message has tool calls but no text")
    void publishesFallbackTextWhenToolCallsWithoutText() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("id-1")
                .name("search")
                .arguments("{}")
                .build();
        AiMessage aiMessage = AiMessage.from(null, List.of(toolRequest));
        ChatResponse response = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder().build())
                .build();
        when(delegate.doChat(any(ChatRequest.class))).thenReturn(response);

        ToolCallTextPublishingChatModel model = new ToolCallTextPublishingChatModel(delegate);
        model.doChat(request());

        // thinkingDuration should be published for the round-trip
        verify(publisher).thinkingDuration(anyLong());

        // toolCallText should be published with the fallback message
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(publisher).toolCallText(anyString(), textCaptor.capture(), eq(false));
        // The fallback text is locale-dependent; just assert it is non-blank
        assertFalse(textCaptor.getValue().isBlank());
    }

    @Test
    @DisplayName("Publishes the actual text when AI message has tool calls and text")
    void publishesActualTextWhenToolCallsWithText() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("id-1")
                .name("search")
                .arguments("{}")
                .build();
        AiMessage aiMessage = AiMessage.from("Let me search the codebase.", List.of(toolRequest));
        ChatResponse response = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder().build())
                .build();
        when(delegate.doChat(any(ChatRequest.class))).thenReturn(response);

        ToolCallTextPublishingChatModel model = new ToolCallTextPublishingChatModel(delegate);
        model.doChat(request());

        // thinkingDuration should be published for the round-trip
        verify(publisher).thinkingDuration(anyLong());

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(publisher).toolCallText(anyString(), textCaptor.capture(), eq(false));
        assertEquals("Let me search the codebase.", textCaptor.getValue());
    }

    @Test
    @DisplayName("Publishes thinkingFailed and rethrows when the delegate fails")
    void publishesThinkingFailedWhenDelegateThrows() {
        when(delegate.doChat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        ToolCallTextPublishingChatModel model = new ToolCallTextPublishingChatModel(delegate);
        assertThrows(RuntimeException.class, () -> model.doChat(request()));

        // The failure must be surfaced so the UI clears the "thinking..." placeholder
        verify(publisher).thinkingFailed(anyLong());
        // No tool call text is published for a failed round-trip
        verify(publisher, never()).toolCallText(anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("Does not publish thinkingFailed outside an active status context")
    void doesNotPublishThinkingFailedWithoutContext() {
        context.deactivate();
        when(delegate.doChat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        ToolCallTextPublishingChatModel model = new ToolCallTextPublishingChatModel(delegate);
        assertThrows(RuntimeException.class, () -> model.doChat(request()));

        verify(publisher, never()).thinkingFailed(anyLong());
    }

    @Test
    @DisplayName("Does not publish toolCallText for final answer without tool calls")
    void doesNotPublishToolCallTextForFinalAnswer() {
        AiMessage aiMessage = AiMessage.from("Here is the answer.");
        ChatResponse response = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder().build())
                .build();
        when(delegate.doChat(any(ChatRequest.class))).thenReturn(response);

        ToolCallTextPublishingChatModel model = new ToolCallTextPublishingChatModel(delegate);
        model.doChat(request());

        // thinkingDuration still published for the round-trip
        verify(publisher).thinkingDuration(anyLong());
        // no toolCallText for the final answer (no thinking content)
        verify(publisher, never()).toolCallText(anyString(), anyString(), anyBoolean());
    }
}
