package com.ooooyt.babycommander.ui.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlashCommandRegistryTest {

    @Test
    void allCommandsStartWithSlash() {
        for (SlashCommandRegistry.SlashCommand c : SlashCommandRegistry.commands()) {
            assertTrue(c.name().startsWith("/"),
                "command name should start with '/': " + c.name());
        }
    }

    @Test
    void filterEmptyReturnsAll() {
        List<SlashCommandRegistry.SlashCommand> all = SlashCommandRegistry.commands();
        assertEquals(all.size(), SlashCommandRegistry.filter("").size());
    }

    @Test
    void filterSlashOnlyReturnsAll() {
        List<SlashCommandRegistry.SlashCommand> all = SlashCommandRegistry.commands();
        assertEquals(all.size(), SlashCommandRegistry.filter("/").size());
    }

    @Test
    void filterLangPrefix() {
        List<SlashCommandRegistry.SlashCommand> matches = SlashCommandRegistry.filter("/l");
        assertEquals(1, matches.size(), "/l should match only /lang");
        assertEquals("/lang", matches.get(0).name());
    }

    @Test
    void filterIsCaseInsensitive() {
        List<SlashCommandRegistry.SlashCommand> upper = SlashCommandRegistry.filter("/L");
        assertEquals(1, upper.size());
        assertEquals("/lang", upper.get(0).name());
    }

    @Test
    void filterExactCommand() {
        List<SlashCommandRegistry.SlashCommand> matches = SlashCommandRegistry.filter("/help");
        assertEquals(1, matches.size());
        assertEquals("/help", matches.get(0).name());
    }

    @Test
    void filterNoMatchReturnsEmpty() {
        assertTrue(SlashCommandRegistry.filter("/zzz").isEmpty());
    }

    @Test
    void filterExitAndQuitShareExitPrefix() {
        // /e should match /exit only; /q should match /quit only.
        assertEquals(1, SlashCommandRegistry.filter("/e").size());
        assertEquals(1, SlashCommandRegistry.filter("/q").size());
    }

    @Test
    void descriptionsAreNonEmpty() {
        for (SlashCommandRegistry.SlashCommand c : SlashCommandRegistry.commands()) {
            assertFalse(c.description().isBlank(),
                "description for " + c.name() + " should not be blank");
        }
    }

    @Test
    void registryIncludesCoreCommands() {
        List<String> names = SlashCommandRegistry.commands().stream()
            .map(SlashCommandRegistry.SlashCommand::name).toList();
        assertTrue(names.contains("/help"));
        assertTrue(names.contains("/exit"));
        assertTrue(names.contains("/lang"));
        assertTrue(names.contains("/history"));
        assertTrue(names.contains("/cnc"));
        assertTrue(names.contains("/workspace"));
    }
}
