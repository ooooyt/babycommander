package com.ooooyt.babycommander.agent;

import com.ooooyt.babycommander.util.TokenCounter;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.logging.Log;

public class TokenUsageLogger implements ChatModelListener {

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null) {
            Log.infof("[TokenUsage] input=%d, output=%d, total=%d",
                tokenUsage.inputTokenCount(),
                tokenUsage.outputTokenCount(),
                tokenUsage.totalTokenCount());

            TokenCounter.calibrateFromUsage(
                tokenUsage,
                responseContext.chatRequest().messages()
            );
        }
    }
}
