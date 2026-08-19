package com.ooooyt.babycommander.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

public class TokenCounter {

    private static final double DEFAULT_CHARS_PER_TOKEN = 3.5;
    private static volatile double globalCalibrationFactor = 1.0;
    private static volatile double globalCharsPerToken = DEFAULT_CHARS_PER_TOKEN;
    private static volatile int globalSampleCount;

    public static void calibrateFromUsage(TokenUsage usage, List<ChatMessage> messages) {
        if (usage == null || messages == null || messages.isEmpty()) {
            return;
        }
        double chars = 0;
        for (ChatMessage msg : messages) {
            String text = extractStaticText(msg);
            if (text != null) {
                chars += text.length();
            }
        }
        if (chars == 0) {
            return;
        }
        int actualTokens = usage.inputTokenCount();
        if (actualTokens <= 0) {
            return;
        }
        double rawCharsPerToken = chars / actualTokens;
        globalCharsPerToken = (globalCharsPerToken * globalSampleCount + rawCharsPerToken) / (globalSampleCount + 1);
        double rawFactor = (double) actualTokens / (chars / DEFAULT_CHARS_PER_TOKEN);
        globalCalibrationFactor = (globalCalibrationFactor * globalSampleCount + rawFactor) / (globalSampleCount + 1);
        globalSampleCount++;
    }

    public static double getGlobalCharsPerToken() {
        return globalCharsPerToken;
    }

    public static double getGlobalCalibrationFactor() {
        return globalCalibrationFactor;
    }

    public static int getGlobalSampleCount() {
        return globalSampleCount;
    }

    private static String extractStaticText(ChatMessage message) {
        if (message instanceof SystemMessage sm) {
            return sm.text();
        }
        if (message instanceof UserMessage um) {
            return um.singleText();
        }
        if (message instanceof AiMessage ai) {
            StringBuilder sb = new StringBuilder();
            if (ai.text() != null) {
                sb.append(ai.text());
            }
            if (ai.thinking() != null) {
                sb.append(ai.thinking());
            }
            if (ai.hasToolExecutionRequests()) {
                for (var req : ai.toolExecutionRequests()) {
                    if (req.name() != null) {
                        sb.append(req.name());
                    }
                    if (req.arguments() != null) {
                        sb.append(req.arguments());
                    }
                }
            }
            return sb.toString();
        }
        if (message instanceof ToolExecutionResultMessage tr) {
            return tr.text();
        }
        return message.toString();
    }

    public int estimateTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage msg : messages) {
            total += estimateTokens(msg);
        }
        return total;
    }

    public int estimateTokens(ChatMessage message) {
        String text = extractText(message);
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double cpt = globalSampleCount > 0 ? globalCharsPerToken : DEFAULT_CHARS_PER_TOKEN;
        int estimated = (int) Math.ceil(text.length() / cpt);
        return Math.max(1, estimated);
    }

    private String extractText(ChatMessage message) {
        return extractStaticText(message);
    }

    public void reset() {
    }
}
