package com.ooooyt.babycommander;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import java.nio.file.Path;
import java.time.LocalTime;

public final class BootLog {

    public static volatile boolean enabled = false;

    private BootLog() {}

    public static void log(String msg) {
        if (!enabled) return;
        String line = LocalTime.now() + " [" + Thread.currentThread().getName() + "] " + msg;
        System.err.println("[boot] " + line);
        System.err.flush();
        try {
            Files.createDirectories(Path.of("logs"));
            try (PrintWriter w = new PrintWriter(new FileWriter("logs/boot.log", true))) {
                w.println(line);
            }
        } catch (Exception e) {
            System.err.println(I18n.tr(MessageKey.BOOT_LOG_FILE_FAILED, e.getMessage()));
        }
    }
}
