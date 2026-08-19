package com.ooooyt.babycommander.status;

import com.ooooyt.babycommander.util.I18n;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class StatusEventPublisherTest {

    @Inject
    StatusEventPublisher publisher;

    @Inject
    EventBus eventBus;

    @BeforeAll
    static void setLocaleToEnglish() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    @Timeout(10)
    void testWorkflowStartedPublishes() throws InterruptedException {
        List<io.vertx.core.json.JsonObject> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        eventBus.consumer(StatusEventPublisher.ADDRESS).handler(msg -> {
            received.add((io.vertx.core.json.JsonObject) msg.body());
            latch.countDown();
        });

        publisher.workflowStarted("SINGLE", "Test task");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, received.size());
        assertEquals("WORKFLOW_STARTED", received.get(0).getString("eventType"));
        assertEquals("SINGLE", received.get(0).getString("workflowType"));
        assertEquals("Test task", received.get(0).getString("taskDescription"));
        assertNotNull(received.get(0).getString("timestamp"));
    }

    @Test
    @Timeout(10)
    void testStepLifecycleEvents() throws InterruptedException {
        List<io.vertx.core.json.JsonObject> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        eventBus.consumer(StatusEventPublisher.ADDRESS).handler(msg -> {
            received.add((io.vertx.core.json.JsonObject) msg.body());
            latch.countDown();
        });

        publisher.stepStarted("step-1", "planner", "Plan the code");
        publisher.stepCompleted("step-1", "planner", 1200);
        publisher.stepFailed("step-2", "writer", "Something went wrong");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(3, received.size());
        assertEquals("STEP_STARTED", received.get(0).getString("eventType"));
        assertEquals("STEP_COMPLETED", received.get(1).getString("eventType"));
        assertEquals(1200L, received.get(1).getLong("durationMs"));
        assertEquals("STEP_FAILED", received.get(2).getString("eventType"));
        assertEquals("writer", received.get(2).getString("agentRole"));
    }

    @Test
    @Timeout(10)
    void testToolCallEvents() throws InterruptedException {
        List<io.vertx.core.json.JsonObject> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        eventBus.consumer(StatusEventPublisher.ADDRESS).handler(msg -> {
            received.add((io.vertx.core.json.JsonObject) msg.body());
            latch.countDown();
        });

        publisher.toolCallStart("step-1", "write_file", "output/main.py");
        publisher.toolCallResult("step-1", "write_file", "output/main.py", "Wrote 100 bytes", 50);
        publisher.toolCallError("step-1", "shell", "rm -rf /", "Permission denied");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(3, received.size());
        assertEquals("TOOL_CALL_START", received.get(0).getString("eventType"));
        assertEquals("write_file", received.get(0).getString("toolName"));
        assertEquals("TOOL_CALL_RESULT", received.get(1).getString("eventType"));
        assertEquals("TOOL_CALL_ERROR", received.get(2).getString("eventType"));
    }

    @Test
    @Timeout(10)
    void testTruncationOfLongStrings() throws InterruptedException {
        List<io.vertx.core.json.JsonObject> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        eventBus.consumer(StatusEventPublisher.ADDRESS).handler(msg -> {
            received.add((io.vertx.core.json.JsonObject) msg.body());
            latch.countDown();
        });

        String longString = "A".repeat(500);
        publisher.stepStarted("step-1", "planner", longString);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        String instruction = received.get(0).getString("instruction");
        // instruction should be shorter than the original 500 chars
        assertTrue(instruction.length() < 500, "instruction should be truncated");
        // instruction should be longer than the max 200 but less than 500
        assertTrue(instruction.length() > 200, "truncated instruction should be > 200 chars");
        assertTrue(instruction.length() < 500, "truncated instruction should be < 500 chars");
        // instruction should end with the truncation suffix (not just raw substring)
        assertTrue(instruction.contains("..."), "truncated instruction should contain '...'");
    }
}
