package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.hook.DangerLevel;
import com.ooooyt.babycommander.hook.ToolCallInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class UiEventTest {

    @Test
    void testMessageOutput() {
        UiEvent event = new UiEvent.MessageOutput("content", UiEvent.MessageType.MARKDOWN, 100L);
        assertInstanceOf(UiEvent.MessageOutput.class, event);
        assertEquals("content", ((UiEvent.MessageOutput) event).content());
        assertEquals(UiEvent.MessageType.MARKDOWN, ((UiEvent.MessageOutput) event).type());
        assertEquals(100L, ((UiEvent.MessageOutput) event).durationMs());
    }

    @Test
    void testMessageOutputCompact() {
        UiEvent event = new UiEvent.MessageOutput("content", UiEvent.MessageType.PLAIN);
        assertEquals("content", ((UiEvent.MessageOutput) event).content());
        assertEquals(UiEvent.MessageType.PLAIN, ((UiEvent.MessageOutput) event).type());
        assertEquals(0L, ((UiEvent.MessageOutput) event).durationMs());
    }

    @Test
    void testMessageOutputCode() {
        UiEvent event = new UiEvent.MessageOutput("code", UiEvent.MessageType.CODE);
        assertEquals(UiEvent.MessageType.CODE, ((UiEvent.MessageOutput) event).type());
    }

    @Test
    void testStreamFragment() {
        UiEvent event = new UiEvent.StreamFragment("token");
        assertInstanceOf(UiEvent.StreamFragment.class, event);
        assertEquals("token", ((UiEvent.StreamFragment) event).token());
    }

    @Test
    void testStreamFragmentEmpty() {
        UiEvent event = new UiEvent.StreamFragment("");
        assertEquals("", ((UiEvent.StreamFragment) event).token());
    }

    @Test
    void testStatusMessage() {
        UiEvent event = new UiEvent.StatusMessage("agent1", "running", 0.5, "processing");
        assertInstanceOf(UiEvent.StatusMessage.class, event);
        assertEquals("agent1", ((UiEvent.StatusMessage) event).agent());
        assertEquals("running", ((UiEvent.StatusMessage) event).status());
        assertEquals(0.5, ((UiEvent.StatusMessage) event).progress(), 0.001);
        assertEquals("processing", ((UiEvent.StatusMessage) event).message());
    }

    @Test
    void testStatusMessageFullProgress() {
        UiEvent event = new UiEvent.StatusMessage("agent", "done", 1.0, "completed");
        assertEquals(1.0, ((UiEvent.StatusMessage) event).progress(), 0.001);
    }

    @Test
    void testConfirmationRequest() {
        UUID id = UUID.randomUUID();
        ToolCallInfo info = new ToolCallInfo("Tool", "method", new Object[]{"arg"}, DangerLevel.SAFE, "s1");
        UiEvent event = new UiEvent.ConfirmationRequest(id, info);
        assertInstanceOf(UiEvent.ConfirmationRequest.class, event);
        assertEquals(id, ((UiEvent.ConfirmationRequest) event).id());
        assertEquals(info, ((UiEvent.ConfirmationRequest) event).info());
    }

    @Test
    void testErrorEvent() {
        UiEvent event = new UiEvent.ErrorEvent("error msg", "cause detail");
        assertInstanceOf(UiEvent.ErrorEvent.class, event);
        assertEquals("error msg", ((UiEvent.ErrorEvent) event).message());
        assertEquals("cause detail", ((UiEvent.ErrorEvent) event).cause());
    }

    @Test
    void testErrorEventNullCause() {
        UiEvent event = new UiEvent.ErrorEvent("error msg", null);
        assertNull(((UiEvent.ErrorEvent) event).cause());
    }

    @Test
    void testSessionEvent() {
        UiEvent event = new UiEvent.SessionEvent(UiEvent.SessionState.STARTED);
        assertInstanceOf(UiEvent.SessionEvent.class, event);
        assertEquals(UiEvent.SessionState.STARTED, ((UiEvent.SessionEvent) event).state());
    }

    @Test
    void testSessionEventStopped() {
        UiEvent event = new UiEvent.SessionEvent(UiEvent.SessionState.STOPPED);
        assertEquals(UiEvent.SessionState.STOPPED, ((UiEvent.SessionEvent) event).state());
    }

    @Test
    void testSessionEventError() {
        UiEvent event = new UiEvent.SessionEvent(UiEvent.SessionState.ERROR);
        assertEquals(UiEvent.SessionState.ERROR, ((UiEvent.SessionEvent) event).state());
    }

    @Test
    void testSessionEventReadyForInput() {
        UiEvent event = new UiEvent.SessionEvent(UiEvent.SessionState.READY_FOR_INPUT);
        assertEquals(UiEvent.SessionState.READY_FOR_INPUT, ((UiEvent.SessionEvent) event).state());
    }

    @Test
    void testSessionEventBusy() {
        UiEvent event = new UiEvent.SessionEvent(UiEvent.SessionState.BUSY);
        assertEquals(UiEvent.SessionState.BUSY, ((UiEvent.SessionEvent) event).state());
    }

    @Test
    void testPlanUpdate() {
        UiEvent.Phase phase1 = new UiEvent.Phase("phase1", "completed");
        UiEvent.Phase phase2 = new UiEvent.Phase("phase2", "running");
        UiEvent event = new UiEvent.PlanUpdate(List.of(phase1, phase2), "test task");
        assertEquals("test task", ((UiEvent.PlanUpdate) event).taskName());
        assertInstanceOf(UiEvent.PlanUpdate.class, event);
        List<UiEvent.Phase> phases = ((UiEvent.PlanUpdate) event).phases();
        assertEquals(2, phases.size());
        assertEquals("phase1", phases.get(0).description());
        assertEquals("completed", phases.get(0).status());
        assertEquals("phase2", phases.get(1).description());
        assertEquals("running", phases.get(1).status());
    }

    @Test
    void testPlanUpdateEmpty() {
        UiEvent event = new UiEvent.PlanUpdate(List.of(), "");
        assertTrue(((UiEvent.PlanUpdate) event).phases().isEmpty());
    }

    @Test
    void testLocaleChange() {
        UiEvent event = new UiEvent.LocaleChange(Locale.CHINA);
        assertInstanceOf(UiEvent.LocaleChange.class, event);
        assertEquals(Locale.CHINA, ((UiEvent.LocaleChange) event).newLocale());
    }

    @Test
    void testLocaleChangeEnglish() {
        UiEvent event = new UiEvent.LocaleChange(Locale.ENGLISH);
        assertEquals(Locale.ENGLISH, ((UiEvent.LocaleChange) event).newLocale());
    }

    @Test
    void testClarificationRequest() {
        UUID id = UUID.randomUUID();
        CompletableFuture<String> future = new CompletableFuture<>();
        UiEvent event = new UiEvent.ClarificationRequest(
            id, "question?", true, List.of("opt1", "opt2"), future
        );
        assertInstanceOf(UiEvent.ClarificationRequest.class, event);
        assertEquals(id, ((UiEvent.ClarificationRequest) event).id());
        assertEquals("question?", ((UiEvent.ClarificationRequest) event).question());
        assertTrue(((UiEvent.ClarificationRequest) event).allowFreeAnswer());
        assertEquals(List.of("opt1", "opt2"), ((UiEvent.ClarificationRequest) event).options());
        assertEquals(future, ((UiEvent.ClarificationRequest) event).future());
    }

    @Test
    void testClarificationRequestNoOptions() {
        UUID id = UUID.randomUUID();
        UiEvent event = new UiEvent.ClarificationRequest(
            id, "question?", false, List.of(), new CompletableFuture<>()
        );
        assertFalse(((UiEvent.ClarificationRequest) event).allowFreeAnswer());
        assertTrue(((UiEvent.ClarificationRequest) event).options().isEmpty());
    }

    @Test
    void testPhase() {
        UiEvent.Phase phase = new UiEvent.Phase("description", "status");
        assertEquals("description", phase.description());
        assertEquals("status", phase.status());
    }

    @Test
    void testMessageTypeValues() {
        assertEquals(3, UiEvent.MessageType.values().length);
        assertEquals(UiEvent.MessageType.MARKDOWN, UiEvent.MessageType.valueOf("MARKDOWN"));
        assertEquals(UiEvent.MessageType.PLAIN, UiEvent.MessageType.valueOf("PLAIN"));
        assertEquals(UiEvent.MessageType.CODE, UiEvent.MessageType.valueOf("CODE"));
    }

    @Test
    void testSessionStateValues() {
        assertEquals(5, UiEvent.SessionState.values().length);
        assertEquals(UiEvent.SessionState.STARTED, UiEvent.SessionState.valueOf("STARTED"));
        assertEquals(UiEvent.SessionState.BUSY, UiEvent.SessionState.valueOf("BUSY"));
        assertEquals(UiEvent.SessionState.STOPPED, UiEvent.SessionState.valueOf("STOPPED"));
        assertEquals(UiEvent.SessionState.ERROR, UiEvent.SessionState.valueOf("ERROR"));
        assertEquals(UiEvent.SessionState.READY_FOR_INPUT, UiEvent.SessionState.valueOf("READY_FOR_INPUT"));
    }

    @Test
    void testSealedInterface() {
        assertTrue(UiEvent.class.isSealed());
    }
}
