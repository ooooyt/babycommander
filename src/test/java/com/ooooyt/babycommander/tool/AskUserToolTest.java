package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.ui.UiEvent;
import com.ooooyt.babycommander.util.I18n;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.core.eventbus.MessageConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AskUserToolTest {

    private AskUserTool tool;

    @BeforeAll
    static void setupLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @BeforeEach
    void setUp() {
        tool = new AskUserTool(null);
    }

    @Test
    void testAskWithoutEventBusReturnsError() {
        String result = tool.ask("Test question?", false);
        assertTrue(result.startsWith("Error"));
    }

    @Test
    void testAskPublishesEvent() {
        AtomicReference<Object> captured = new AtomicReference<>();
        EventBus fakeBus = new EventBus((io.vertx.core.eventbus.EventBus) null) {
            public EventBus publish(String address, Object message) {
                captured.set(message);
                return this;
            }
            public <T> MessageConsumer<T> consumer(String address, Consumer<io.vertx.mutiny.core.eventbus.Message<T>> handler) { return null; }
            public <T> MessageConsumer<T> consumer(String address) { return null; }
        };
        AskUserTool t = new AskUserTool(fakeBus);
        Thread runner = new Thread(() -> t.ask("Pick one?", false, "A", "B"));
        runner.start();
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        Object msg = captured.get();
        assertInstanceOf(UiEvent.ClarificationRequest.class, msg);
        UiEvent.ClarificationRequest req = (UiEvent.ClarificationRequest) msg;
        assertEquals("Pick one?", req.question());
        assertEquals(2, req.options().size());
        assertEquals("A", req.options().get(0));
        assertEquals("B", req.options().get(1));
        assertFalse(req.allowFreeAnswer());
        req.future().complete("A");
        try { runner.join(1000); } catch (InterruptedException e) {}
    }

    @Test
    void testAskReturnsFutureResult() throws Exception {
        AtomicReference<UiEvent.ClarificationRequest> captured = new AtomicReference<>();
        EventBus fakeBus = new EventBus((io.vertx.core.eventbus.EventBus) null) {
            public EventBus publish(String address, Object message) {
                if (message instanceof UiEvent.ClarificationRequest r) captured.set(r);
                return this;
            }
            public <T> MessageConsumer<T> consumer(String address, Consumer<io.vertx.mutiny.core.eventbus.Message<T>> handler) { return null; }
            public <T> MessageConsumer<T> consumer(String address) { return null; }
        };
        AskUserTool t = new AskUserTool(fakeBus);
        Thread runner = new Thread(() -> {
            String result = t.ask("Test?", true, "X", "Y");
            assertEquals("got it", result);
        });
        runner.start();
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        UiEvent.ClarificationRequest req = captured.get();
        assertNotNull(req);
        req.future().complete("got it");
        runner.join(2000);
    }
}
