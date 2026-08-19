package com.ooooyt.babycommander.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CodegenAgent {

    String chat(@UserMessage String userMessage);

    @SystemMessage("{{systemPrompt}}")
    String chatWithSystemPrompt(@V("systemPrompt") String systemPrompt, @UserMessage String userMessage);
}
