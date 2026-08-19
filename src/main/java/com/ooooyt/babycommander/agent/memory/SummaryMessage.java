package com.ooooyt.babycommander.agent.memory;

import dev.langchain4j.data.message.SystemMessage;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

public class SummaryMessage extends SystemMessage {
    private String content;

    public SummaryMessage(String text) {
        super(text);
        this.content = text;
    }

    public void updateText(String newText) {
        this.content = newText;
    }

    @Override
    public String text() {
        return content;
    }

    @Override
    public String toString() {
        return I18n.tr(MessageKey.SUMMARY_MESSAGE_TO_STRING, content);
    }
}
