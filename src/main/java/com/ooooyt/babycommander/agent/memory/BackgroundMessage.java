package com.ooooyt.babycommander.agent.memory;

import dev.langchain4j.data.message.SystemMessage;

/**
 * A system message that carries workspace/project background information.
 * Placed before the 3 SummaryMessages in the chat memory so the LLM
 * always sees the project context first.
 */
public class BackgroundMessage extends SystemMessage {

    public BackgroundMessage(String text) {
        super(text);
    }
}
