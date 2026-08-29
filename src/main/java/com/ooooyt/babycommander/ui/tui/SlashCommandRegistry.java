package com.ooooyt.babycommander.ui.tui;

import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the slash commands available in the TUI.
 *
 * <p>Every place that needs to enumerate the available commands &mdash; the
 * {@code /help} output (both in {@link InputProcessor} and in the engine-side
 * {@code CommandHandler}) and the live popup that appears when the user types
 * a {@code /} prefix &mdash; reads from this registry. Adding a new command
 * here therefore makes it show up automatically in the help text <em>and</em>
 * in the popup, with no further edits required.
 *
 * <p>Each entry pairs the command token (e.g. {@code "/lang"}) with the
 * human-readable description already localised through {@link I18n}, so the
 * popup and help output stay in sync with the user's chosen language.
 */
public final class SlashCommandRegistry {

    private SlashCommandRegistry() {}

    /**
     * Immutable snapshot of a single slash command.
     *
     * @param name        the command token the user types, starting with
     *                    {@code /} &mdash; e.g. {@code "/lang"}, {@code "/help"}.
     * @param description the localised one-line description shown in the help
     *                    text and the popup.
     */
    public record SlashCommand(String name, String description) {}

    /**
     * The canonical, ordered list of slash commands.
     *
     * <p>Order matters: it is the display order used by {@code /help} and by
     * the popup. The descriptions are resolved lazily on each call so a
     * locale switch ({@code /lang}) is reflected on the next read without
     * restarting the application.
     */
    public static List<SlashCommand> commands() {
        List<SlashCommand> list = new ArrayList<>();
        list.add(new SlashCommand("/lang",     I18n.tr(MessageKey.CHAT_HELP_LANG)));
        list.add(new SlashCommand("/workspace", I18n.tr(MessageKey.CHAT_WORKSPACE_USAGE)));
        list.add(new SlashCommand("/history",  I18n.tr(MessageKey.CHAT_HELP_HISTORY)));
        list.add(new SlashCommand("/cnc",      I18n.tr(MessageKey.CHAT_HELP_CNC)));
        list.add(new SlashCommand("/config",   I18n.tr(MessageKey.CHAT_HELP_CONFIG)));
        list.add(new SlashCommand("/help",     I18n.tr(MessageKey.CHAT_HELP_HELP)));
        list.add(new SlashCommand("/exit",     I18n.tr(MessageKey.CHAT_HELP_EXIT)));
        list.add(new SlashCommand("/quit",     I18n.tr(MessageKey.CHAT_HELP_QUIT)));
        return list;
    }

    /**
     * Returns the commands whose name starts with the given prefix (case-
     * insensitive). When the prefix is just {@code "/"} (or empty) all
     * commands are returned, preserving the canonical order.
     *
     * <p>This is the filtering entry point used by the live popup: as the
     * user types more characters after the {@code /}, the list shrinks to
     * the matching subset.
     *
     * @param prefix the text typed so far, e.g. {@code "/"}, {@code "/l"} or
     *               {@code "/lan"}; may be empty or {@code null} (treated as
     *               empty) to mean "no filter".
     */
    public static List<SlashCommand> filter(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase();
        List<SlashCommand> all = commands();
        if (p.isEmpty() || "/".equals(p)) {
            return all;
        }
        List<SlashCommand> out = new ArrayList<>();
        for (SlashCommand c : all) {
            if (c.name().toLowerCase().startsWith(p)) {
                out.add(c);
            }
        }
        return out;
    }
}
