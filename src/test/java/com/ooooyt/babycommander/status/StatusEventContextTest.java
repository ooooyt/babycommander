package com.ooooyt.babycommander.status;

import io.vertx.mutiny.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StatusEventContextTest {

    @Mock
    EventBus eventBus;

    @Mock
    StatusEventPublisher mockPublisher;

    private StatusEventContext context;

    @BeforeEach
    void setUp() {
        context = new StatusEventContext(mockPublisher);
    }

    @Test
    void testActivateAndGet() {
        context.activate();
        assertNotNull(StatusEventContext.get());
        assertSame(mockPublisher, StatusEventContext.get());
    }

    @Test
    void testDeactivate() {
        context.activate();
        assertNotNull(StatusEventContext.get());
        context.deactivate();
        assertNull(StatusEventContext.get());
    }

    @Test
    void testGetWithoutActivate() {
        assertNull(StatusEventContext.get());
    }

    @Test
    void testActivateDeactivateMultiple() {
        context.activate();
        assertNotNull(StatusEventContext.get());
        context.deactivate();
        assertNull(StatusEventContext.get());

        // Re-activate should work
        context.activate();
        assertNotNull(StatusEventContext.get());
        context.deactivate();
        assertNull(StatusEventContext.get());
    }

    @Test
    void testThreadLocalIsolation() throws Exception {
        // Verify that ThreadLocal doesn't leak across threads
        context.activate();
        assertNotNull(StatusEventContext.get());

        Thread otherThread = new Thread(() -> assertNull(StatusEventContext.get()));
        otherThread.start();
        otherThread.join();

        context.deactivate();
        assertNull(StatusEventContext.get());
    }
}
