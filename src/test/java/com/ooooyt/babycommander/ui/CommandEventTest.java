package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.hook.ConfirmationResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommandEventTest {

    @Test
    void testUserInput() {
        CommandEvent event = new CommandEvent.UserInput("hello");
        assertInstanceOf(CommandEvent.UserInput.class, event);
        assertEquals("hello", ((CommandEvent.UserInput) event).text());
    }

    @Test
    void testUserInputEmpty() {
        CommandEvent event = new CommandEvent.UserInput("");
        assertEquals("", ((CommandEvent.UserInput) event).text());
    }

    @Test
    void testConfirmationResponse() {
        UUID id = UUID.randomUUID();
        CommandEvent event = new CommandEvent.ConfirmationResponse(id, ConfirmationResult.ALLOW);
        assertInstanceOf(CommandEvent.ConfirmationResponse.class, event);
        assertEquals(id, ((CommandEvent.ConfirmationResponse) event).requestId());
        assertEquals(ConfirmationResult.ALLOW, ((CommandEvent.ConfirmationResponse) event).result());
    }

    @Test
    void testConfirmationResponseDeny() {
        UUID id = UUID.randomUUID();
        CommandEvent event = new CommandEvent.ConfirmationResponse(id, ConfirmationResult.DENY);
        assertEquals(ConfirmationResult.DENY, ((CommandEvent.ConfirmationResponse) event).result());
    }

    @Test
    void testConfirmationResponseAllowAlways() {
        UUID id = UUID.randomUUID();
        CommandEvent event = new CommandEvent.ConfirmationResponse(id, ConfirmationResult.ALLOW_ALWAYS);
        assertEquals(ConfirmationResult.ALLOW_ALWAYS, ((CommandEvent.ConfirmationResponse) event).result());
    }

    @Test
    void testCancel() {
        CommandEvent event = new CommandEvent.Cancel();
        assertInstanceOf(CommandEvent.Cancel.class, event);
    }

    @Test
    void testSessionCommandStart() {
        CommandEvent event = new CommandEvent.SessionCommand(CommandEvent.SessionAction.START);
        assertInstanceOf(CommandEvent.SessionCommand.class, event);
        assertEquals(CommandEvent.SessionAction.START, ((CommandEvent.SessionCommand) event).action());
    }

    @Test
    void testSessionCommandStop() {
        CommandEvent event = new CommandEvent.SessionCommand(CommandEvent.SessionAction.STOP);
        assertEquals(CommandEvent.SessionAction.STOP, ((CommandEvent.SessionCommand) event).action());
    }

    @Test
    void testClarificationResponse() {
        UUID id = UUID.randomUUID();
        CommandEvent event = new CommandEvent.ClarificationResponse(id, "question?", "answer");
        assertInstanceOf(CommandEvent.ClarificationResponse.class, event);
        assertEquals(id, ((CommandEvent.ClarificationResponse) event).id());
        assertEquals("question?", ((CommandEvent.ClarificationResponse) event).question());
        assertEquals("answer", ((CommandEvent.ClarificationResponse) event).answer());
    }

    @Test
    void testSessionActionValues() {
        assertEquals(2, CommandEvent.SessionAction.values().length);
        assertEquals(CommandEvent.SessionAction.START, CommandEvent.SessionAction.valueOf("START"));
        assertEquals(CommandEvent.SessionAction.STOP, CommandEvent.SessionAction.valueOf("STOP"));
    }

    @Test
    void testSealedInterface() {
        // Verify that all subtypes are known
        assertTrue(CommandEvent.class.isSealed());
    }
}
