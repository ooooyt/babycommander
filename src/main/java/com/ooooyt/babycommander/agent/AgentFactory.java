package com.ooooyt.babycommander.agent;

import com.ooooyt.babycommander.CodeGenLifecycle;
import com.ooooyt.babycommander.agent.memory.ConversationCompactor;
import com.ooooyt.babycommander.agent.memory.ToolCallAwareChatMemory;
import com.ooooyt.babycommander.config.AgentConfig;
import com.ooooyt.babycommander.config.AgentConfig.ProviderConfig;
import com.ooooyt.babycommander.config.YamlConfigLoader;
import com.ooooyt.babycommander.hook.SessionMemory;
import com.ooooyt.babycommander.service.ProjectTaskService;
import com.ooooyt.babycommander.tool.PlanTool;
import com.ooooyt.babycommander.tool.ResilientToolExecutor;
import com.ooooyt.babycommander.tool.ToolRegistry;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AgentFactory {

    private final YamlConfigLoader configLoader;
    private final ToolRegistry toolRegistry;
    private final SessionMemory sessionMemory;
    private final CodeGenLifecycle lifecycle;
    private final ConversationCompactor conversationCompactor;
    private final ProjectTaskService projectTaskService;
    private final Map<String, AgentContext> activeAgents = new ConcurrentHashMap<>();
    private static final int SEQUENTIAL_TOOLS_LIMIT = 500;

    /**
     * CDI constructor. {@code projectTaskService} is passed through to each
     * {@link ResilientToolExecutor} so tool executions can be persisted.
     */
    @Inject
    public AgentFactory(YamlConfigLoader configLoader, ToolRegistry toolRegistry, SessionMemory sessionMemory,
                        CodeGenLifecycle lifecycle, ConversationCompactor conversationCompactor,
                        ProjectTaskService projectTaskService) {
        this.configLoader = configLoader;
        this.toolRegistry = toolRegistry;
        this.sessionMemory = sessionMemory;
        this.lifecycle = lifecycle;
        this.conversationCompactor = conversationCompactor;
        this.projectTaskService = projectTaskService;
    }

    /**
     * Backward-compatible constructor for tests that do not need tool-execution
     * persistence. Delegates to the full constructor with a {@code null} service.
     */
    public AgentFactory(YamlConfigLoader configLoader, ToolRegistry toolRegistry, SessionMemory sessionMemory,
                        CodeGenLifecycle lifecycle, ConversationCompactor conversationCompactor) {
        this(configLoader, toolRegistry, sessionMemory, lifecycle, conversationCompactor, null);
    }

    private AgentConfig config() {
        return configLoader.getConfig();
    }

    /**
     * Builds a {@code Map<ToolSpecification, ToolExecutor>} for all registered
     * tools. Each tool is executed through a {@link ResilientToolExecutor} so
     * that malformed JSON produced by the LLM for tool-call arguments (e.g. a
     * missing comma between array entries) is repaired instead of aborting the
     * whole agent session with a {@code JsonParseException}.
     */
    private Map<ToolSpecification, ToolExecutor> buildToolExecutorMap(List<Object> agentTools) {
        Map<ToolSpecification, ToolExecutor> executors = new HashMap<>();
        for (Object tool : agentTools) {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(tool);
            for (ToolSpecification spec : specs) {
                executors.put(spec, new ResilientToolExecutor(tool, ToolExecutionRequest.builder()
                        .name(spec.name())
                        .arguments("{}")
                        .build(), projectTaskService));
            }
        }
        return executors;
    }

    public AgentContext createAgent(String role, String sessionId, String projectFolder) {
        // Ensure tools, MCP, and skills are initialized before creating an agent
        lifecycle.initialize();

        AgentConfig config = config();
        AgentConfig.AgentRoleConfig roleConfig = config.agentRoles.get(role);
        if (roleConfig == null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_ROLE_NOT_CONFIGURED, role));
        }
        if (roleConfig.systemPrompt == null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_PROMPT_NOT_CONFIGURED, role));
        }

        String providerName = roleConfig.provider != null ? roleConfig.provider : config.agentDefaults.provider;
        AgentConfig.ProviderConfig providerConfig = config.providers.get(providerName);
        if (providerConfig == null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_PROVIDER_NOT_CONFIGURED, providerName));
        }

        ChatModel model = createModel(providerConfig);
        String systemPrompt = roleConfig.systemPrompt
                .replace("{workspace}", projectFolder)
                .replace("{role}", role);

        int maxToolCalls = config.agentDefaults.maxToolCalls;
        int maxTokensInMemory = config.agentDefaults.maxTokensInMemory;

        List<Object> agentTools = toolRegistry.getAllTools();

        ChatMemory chatMemory = new ToolCallAwareChatMemory(sessionId, maxTokensInMemory);
        if (chatMemory instanceof ToolCallAwareChatMemory tcm) {
            tcm.setOnMemoryFull(mem -> {
                conversationCompactor.compactMemory(mem);
                mem.ejectOldMessages(maxToolCalls);
            });
            tcm.setOnPhaseSummaryCheck(mem -> mem.updatePhaseSummary(PlanTool.getLatestSnapshot()));
        }

        AiServices<CodegenAgent> aiBuilder = AiServices.builder(CodegenAgent.class)
                .chatModel(model)
                .chatMemoryProvider(memoryId -> chatMemory)
                .maxSequentialToolsInvocations(SEQUENTIAL_TOOLS_LIMIT)
                .systemMessageProvider(memoryId -> systemPrompt)
                .hallucinatedToolNameStrategy((ToolExecutionRequest req) ->
                    ToolExecutionResultMessage.from(req, I18n.tr(MessageKey.AGENT_TOOL_NOT_FOUND, req.name())));

        if (!agentTools.isEmpty()) {
            aiBuilder.tools(buildToolExecutorMap(agentTools));
        }

        CodegenAgent agent = aiBuilder.build();

        AgentContext ctx = AgentContext.of(sessionId, agent, projectFolder, role, agentTools, chatMemory);
        AgentContext existing = activeAgents.putIfAbsent(sessionId, ctx);
        if (existing != null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_SESSION_EXISTS, sessionId));
        }

        return ctx;
    }

    /**
     * Creates a lightweight ChatModel suitable for semantic search / ranking tasks.
     * Uses the default provider configuration with temperature=0 for deterministic output.
     */
    public ChatModel createSearchModel() {
        lifecycle.initialize();
        String providerName = resolveDefaultProvider();
        AgentConfig.ProviderConfig providerConfig = config().providers.get(providerName);
        if (providerConfig == null) {
            throw new IllegalArgumentException(
                I18n.tr(MessageKey.AGENT_DEFAULT_PROVIDER_NOT_CONFIGURED, providerName));
        }
        // Use lower temperature for more deterministic ranking
        ProviderConfig searchConfig = new ProviderConfig();
        searchConfig.type = providerConfig.type;
        searchConfig.baseUrl = providerConfig.baseUrl;
        searchConfig.apiKey = providerConfig.apiKey;
        searchConfig.modelName = providerConfig.modelName;
        searchConfig.temperature = 0.0;
        searchConfig.maxTokens = 1024;
        searchConfig.timeoutSeconds = providerConfig.timeoutSeconds;
        searchConfig.maxRetries = providerConfig.maxRetries;
        return createModel(searchConfig);
    }

    public AgentContext createAgentWithCustomPrompt(String customPrompt, String sessionId, String projectFolder) {
        // Ensure tools, MCP, and skills are initialized before creating an agent
        lifecycle.initialize();

        if (customPrompt == null || customPrompt.isBlank()) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_CUSTOM_PROMPT_NULL));
        }

        String providerName = resolveDefaultProvider();
        Log.infof("Chat mode: provider=%s", providerName);
        AgentConfig.ProviderConfig providerConfig = config().providers.get(providerName);
        if (providerConfig == null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_DEFAULT_PROVIDER_NOT_CONFIGURED, providerName));
        }

        Log.infof("----> Config: %s", providerConfig);
        ChatModel model = createModel(providerConfig);
        int maxToolCalls = config().agentDefaults.maxToolCalls;
        int maxTokensInMemory = config().agentDefaults.maxTokensInMemory;

        List<Object> agentTools = toolRegistry.getAllTools();

        ChatMemory chatMemory = new ToolCallAwareChatMemory(sessionId, maxTokensInMemory);
        if (chatMemory instanceof ToolCallAwareChatMemory tcm) {
            tcm.setOnMemoryFull(mem -> {
                conversationCompactor.compactMemory(mem);
                mem.ejectOldMessages(maxToolCalls);
            });
            tcm.setOnPhaseSummaryCheck(mem -> mem.updatePhaseSummary(PlanTool.getLatestSnapshot()));
        }

        String resolvedPrompt = customPrompt.replace("{workspace}", projectFolder);
        AiServices<CodegenAgent> aiBuilder = AiServices.builder(CodegenAgent.class)
                .chatModel(model)
                .chatMemoryProvider(memoryId -> chatMemory)
                .maxSequentialToolsInvocations(SEQUENTIAL_TOOLS_LIMIT)
                .systemMessageProvider(memoryId -> resolvedPrompt)
                .hallucinatedToolNameStrategy((ToolExecutionRequest req) ->
                    ToolExecutionResultMessage.from(req, I18n.tr(MessageKey.AGENT_TOOL_NOT_FOUND, req.name())));

        if (!agentTools.isEmpty()) {
            aiBuilder.tools(buildToolExecutorMap(agentTools));
        }

        CodegenAgent agent = aiBuilder.build();
        AgentContext ctx = AgentContext.of(sessionId, agent, projectFolder, "chat", agentTools, chatMemory);
        AgentContext existing = activeAgents.putIfAbsent(sessionId, ctx);
        if (existing != null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_SESSION_EXISTS, sessionId));
        }

        return ctx;
    }

    public void disposeAgent(String sessionId) {
        AgentContext removed = activeAgents.remove(sessionId);
        if (removed == null) {
            throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_NO_ACTIVE_SESSION, sessionId));
        }
        if (sessionMemory != null) {
            sessionMemory.clearSession(sessionId);
        }
    }

    public List<String> getActiveSessions() {
        return List.copyOf(activeAgents.keySet());
    }

    public int getActiveAgentCount() {
        return activeAgents.size();
    }

    private String resolveDefaultProvider() {
        AgentConfig config = config();
        String defaultModel = config.defaultModel;
        if (defaultModel != null && !defaultModel.isBlank()
                && config.providers != null && config.providers.containsKey(defaultModel)) {
            return defaultModel;
        }
        return config.agentDefaults.provider;
    }

    private ChatModel createModel(AgentConfig.ProviderConfig c) {
        Duration timeout = Duration.ofSeconds(c.timeoutSeconds);
        int maxRetries = resolveMaxRetries(c);
        ChatModel model = switch (c.type) {
            case "openai" -> OpenAiChatModel.builder()
                    .baseUrl(c.baseUrl == null || c.baseUrl.isEmpty() ? null : c.baseUrl)
                    .apiKey(c.apiKey)
                    .modelName(c.modelName)
                    .temperature(c.temperature)
                    .maxRetries(maxRetries)
                    .maxTokens(c.maxTokens)
                    .timeout(timeout)
                    .returnThinking(false)
                    .build();
            case "deepseek" -> DeepSeekChatModel.builder()
                    .baseUrl(c.baseUrl)
                    .apiKey(c.apiKey)
                    .modelName(c.modelName)
                    .maxRetries(maxRetries)
                    .temperature(c.temperature)
                    .maxTokens(c.maxTokens)
                    .timeout(timeout)
                    .build();
            case "anthropic" -> AnthropicChatModel.builder()
                    .baseUrl(c.baseUrl == null || c.baseUrl.isEmpty() ? null : c.baseUrl)
                    .apiKey(c.apiKey)
                    .modelName(c.modelName)
                    .temperature(c.temperature)
                    .maxTokens(c.maxTokens)
                    .timeout(timeout)
                    .build();
            case "ollama" -> OllamaChatModel.builder()
                    .baseUrl(c.baseUrl)
                    .modelName(c.modelName)
                    .temperature(c.temperature)
                    .numPredict(c.maxTokens)
                    .timeout(timeout)
                    .build();
            default -> throw new IllegalArgumentException(I18n.tr(MessageKey.AGENT_UNSUPPORTED_PROVIDER, c.type));
        };
        // Wrap so that descriptive text accompanying tool-call requests is
        // surfaced as a TOOL_CALL_TEXT status event before the tools run.
        return new ToolCallTextPublishingChatModel(model);
    }

    /**
     * Resolves the number of LLM request retries from config (clamped to a sane
     * range). With long read timeouts, langchain4j's default of 2 retries can
     * leave the UI hanging for many minutes after a timeout; keeping retries
     * low bounds worst-case latency. 0 disables retries entirely.
     */
    private static int resolveMaxRetries(ProviderConfig c) {
        return Math.max(0, Math.min(c.maxRetries, 5));
    }
}
