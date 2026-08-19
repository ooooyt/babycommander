package com.ooooyt.babycommander.intent;

import com.ooooyt.babycommander.agent.AgentContext;
import com.ooooyt.babycommander.agent.AgentFactory;
import com.ooooyt.babycommander.agent.CodegenAgent;
import com.ooooyt.babycommander.model.AgentRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplexityRouterTest {

    @Mock
    AgentFactory agentFactory;

    @Mock
    AgentContext agentContext;

    @Mock
    CodegenAgent codegenAgent;

    private ComplexityRouter router;

    @BeforeEach
    void setUp() {
        router = new ComplexityRouter(agentFactory);
    }

    @Test
    void testIsComplex_SimpleTask_LowScore() {
        // "hello" has very few keywords, score will be 1 (one meaningful sentence)
        boolean result = router.isComplex("hello", "session1", "/tmp/project");
        assertFalse(result);
        verifyNoInteractions(agentFactory);
    }

    @Test
    void testIsComplex_ComplexTask_HighScore() {
        // Multiple structural keywords should score >= 6
        String task = "Design a microservice architecture with distributed pipeline " +
                      "deployment and scalable infrastructure";
        boolean result = router.isComplex(task, "session1", "/tmp/project");
        assertTrue(result);
        verifyNoInteractions(agentFactory);
    }

    @Test
    void testIsComplex_BorderlineScore_ConsultRouter() {
        // Score exactly 3 (1 structural keyword "architecture" = 3)
        // Plus 1 for meaningful sentence = 4... Let me check.
        // Actually "architecture" is structural (3), "and" is conjunction (1), sentence (1) = 5
        // Still borderline (3 <= 5 < 6)
        when(agentFactory.createAgent(anyString(), anyString(), anyString())).thenReturn(agentContext);
        when(agentContext.agent()).thenReturn(codegenAgent);
        when(agentContext.sessionId()).thenReturn("session1");
        when(codegenAgent.chatWithSystemPrompt(anyString(), anyString())).thenReturn("SIMPLE: straightforward change");

        boolean result = router.isComplex("architecture", "session1", "/tmp/project");

        verify(agentFactory).createAgent(eq(AgentRole.ROUTER.getValue()), eq("session1"), eq("/tmp/project"));
        verify(agentFactory).disposeAgent("session1");
        assertFalse(result);
    }

    @Test
    void testIsComplex_BorderlineScore_RouterSaysComplex() {
        when(agentFactory.createAgent(anyString(), anyString(), anyString())).thenReturn(agentContext);
        when(agentContext.agent()).thenReturn(codegenAgent);
        when(agentContext.sessionId()).thenReturn("session1");
        when(codegenAgent.chatWithSystemPrompt(anyString(), anyString())).thenReturn("COMPLEX: involves multiple components");

        boolean result = router.isComplex("architecture", "session1", "/tmp/project");

        verify(agentFactory).createAgent(eq(AgentRole.ROUTER.getValue()), eq("session1"), eq("/tmp/project"));
        verify(agentFactory).disposeAgent("session1");
        assertTrue(result);
    }

    @Test
    void testIsComplex_BorderlineScore_RouterFails() {
        when(agentFactory.createAgent(anyString(), anyString(), anyString())).thenReturn(agentContext);
        when(agentContext.agent()).thenReturn(codegenAgent);
        when(agentContext.sessionId()).thenReturn("session1");
        when(codegenAgent.chatWithSystemPrompt(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        boolean result = router.isComplex("architecture", "session1", "/tmp/project");

        verify(agentFactory).createAgent(eq(AgentRole.ROUTER.getValue()), eq("session1"), eq("/tmp/project"));
        verify(agentFactory).disposeAgent("session1");
        assertFalse(result);
    }

    @Test
    void testIsComplex_NullTask() {
        boolean result = router.isComplex(null, "session1", "/tmp/project");
        assertFalse(result);
        verifyNoInteractions(agentFactory);
    }

    @Test
    void testIsComplex_EmptyTask() {
        boolean result = router.isComplex("", "session1", "/tmp/project");
        assertFalse(result);
        verifyNoInteractions(agentFactory);
    }

    @Test
    void testIsComplex_ScoreExactly3() {
        // "pipeline" is a structural keyword = 3 points
        // Plus 1 for meaningful sentence = 4. Still borderline (4 < 6)
        when(agentFactory.createAgent(anyString(), anyString(), anyString())).thenReturn(agentContext);
        when(agentContext.agent()).thenReturn(codegenAgent);
        when(agentContext.sessionId()).thenReturn("session2");
        when(codegenAgent.chatWithSystemPrompt(anyString(), anyString())).thenReturn("SIMPLE: straightforward change");

        boolean result = router.isComplex("pipeline", "session2", "/tmp/project");

        verify(agentFactory).createAgent(anyString(), eq("session2"), anyString());
        verify(agentFactory).disposeAgent("session2");
    }

    @Test
    void testIsComplex_ScoreExactly5() {
        // "microservice" is structural (3) + "and" is conjunction (1) + 1 sentence = 5
        // Borderline (3 <= 5 < 6)
        when(agentFactory.createAgent(anyString(), anyString(), anyString())).thenReturn(agentContext);
        when(agentContext.agent()).thenReturn(codegenAgent);
        when(agentContext.sessionId()).thenReturn("session3");
        when(codegenAgent.chatWithSystemPrompt(anyString(), anyString())).thenReturn("COMPLEX: involves multiple components");

        boolean result = router.isComplex("microservice and", "session3", "/tmp/project");

        verify(agentFactory).createAgent(anyString(), eq("session3"), anyString());
        verify(agentFactory).disposeAgent("session3");
    }
}
