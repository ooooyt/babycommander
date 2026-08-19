package com.ooooyt.babycommander.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskResultTest {

    @Test
    void testSuccessFactory() {
        TaskResult result = TaskResult.success("/workspace", "all good");
        assertEquals(TaskResult.Status.SUCCESS, result.status());
        assertEquals("all good", result.result());
        assertEquals("/workspace", result.workspace());
        assertTrue(result.outputFiles().isEmpty());
        assertTrue(result.agentExecutions().isEmpty());
    }

    @Test
    void testFailureFactory() {
        TaskResult result = TaskResult.failure("/workspace", "something broke");
        assertEquals(TaskResult.Status.FAILURE, result.status());
        assertEquals("something broke", result.result());
        assertEquals("/workspace", result.workspace());
        assertTrue(result.outputFiles().isEmpty());
        assertTrue(result.agentExecutions().isEmpty());
    }

    @Test
    void testSuccessWithNullWorkspace() {
        TaskResult result = TaskResult.success(null, "ok");
        assertNull(result.workspace());
    }

    @Test
    void testFailureWithNullWorkspace() {
        TaskResult result = TaskResult.failure(null, "error");
        assertNull(result.workspace());
    }

    @Test
    void testFullRecord() {
        TaskResult.AgentExecution exec = TaskResult.AgentExecution.of(
            "agent1", "writer", "write code", "done", Duration.ofSeconds(5)
        );
        TaskResult result = new TaskResult(
            TaskResult.Status.TIMEOUT,
            "timed out",
            "/ws",
            List.of("file1.txt"),
            List.of(exec)
        );
        assertEquals(TaskResult.Status.TIMEOUT, result.status());
        assertEquals("timed out", result.result());
        assertEquals("/ws", result.workspace());
        assertEquals(1, result.outputFiles().size());
        assertEquals("file1.txt", result.outputFiles().get(0));
        assertEquals(1, result.agentExecutions().size());
        assertEquals(exec, result.agentExecutions().get(0));
    }

    @Test
    void testAgentExecutionOf() {
        Duration d = Duration.ofMillis(1500);
        TaskResult.AgentExecution exec = TaskResult.AgentExecution.of("a1", "planner", "plan", "done", d);
        assertEquals("a1", exec.agentId());
        assertEquals("planner", exec.role());
        assertEquals("plan", exec.input());
        assertEquals("done", exec.output());
        assertEquals(d, exec.duration());
    }

    @Test
    void testAgentExecutionEquality() {
        Duration d = Duration.ZERO;
        TaskResult.AgentExecution e1 = TaskResult.AgentExecution.of("a", "r", "i", "o", d);
        TaskResult.AgentExecution e2 = new TaskResult.AgentExecution("a", "r", "i", "o", d);
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void testStatusValues() {
        assertEquals(3, TaskResult.Status.values().length);
        assertTrue(TaskResult.Status.valueOf("SUCCESS") == TaskResult.Status.SUCCESS);
        assertTrue(TaskResult.Status.valueOf("FAILURE") == TaskResult.Status.FAILURE);
        assertTrue(TaskResult.Status.valueOf("TIMEOUT") == TaskResult.Status.TIMEOUT);
    }
}
