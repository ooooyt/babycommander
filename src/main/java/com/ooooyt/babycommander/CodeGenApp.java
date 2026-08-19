package com.ooooyt.babycommander;

import com.ooooyt.babycommander.ui.ChatEngine;
import com.ooooyt.babycommander.ui.UiAdapter;
import com.ooooyt.babycommander.ui.UiAdapterProducer;
import com.ooooyt.babycommander.ui.tui.TerminalUIAdapter;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

@QuarkusMain
public class CodeGenApp implements QuarkusApplication {

    private static volatile TerminalUIAdapter tui;

    @Inject
    ChatEngine chatEngine;

    @Inject
    UiAdapterProducer uiAdapterProducer;

    private static void redirectStderrToFile() {
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup libc = linker.defaultLookup();
            MethodHandle fopen_ = linker.downcallHandle(
                libc.find("fopen").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle fileno_ = linker.downcallHandle(
                libc.find("fileno").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MethodHandle dup2_ = linker.downcallHandle(
                libc.find("dup2").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            MethodHandle fclose_ = linker.downcallHandle(
                libc.find("fclose").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment path = arena.allocateFrom("jvm-warnings.log");
                MemorySegment mode = arena.allocateFrom("a");
                MemorySegment file = (MemorySegment) fopen_.invoke(path, mode);
                if (!file.equals(MemorySegment.NULL)) {
                    int fd = (int) fileno_.invoke(file);
                    if (fd >= 0) {
                        dup2_.invoke(fd, 2);
                    }
                    fclose_.invoke(file);
                }
            }
        } catch (Throwable t) {
            // best effort
        }
        try {
            System.setErr(new PrintStream(new FileOutputStream("jvm-warnings.log", true)));
        } catch (Throwable t) {
            // best effort
        }
    }

    public static void main(String[] args) {
        redirectStderrToFile();
        tui = new TerminalUIAdapter(null, null);
        UiAdapterProducer.setPreCreated(tui);
        BootLog.log("TUI created, launching on tui-thread");

        Thread tuiThread = new Thread(() -> {
            BootLog.log("TUI start() begin");
            tui.start();
            BootLog.log("TUI start() returned");
        }, "tui-thread");
        tuiThread.setDaemon(false);
        tuiThread.start();
        BootLog.log("TUI thread spawned, now calling Quarkus.run");

        Quarkus.run(CodeGenApp.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        BootLog.log("run() TUI mode: starting ChatEngine");
        UiAdapter adapter = uiAdapterProducer.produceUiAdapter();
        String ws = System.getProperty("user.dir");
        chatEngine.start(adapter, ws, ws);
        BootLog.log("ChatEngine.start returned");
        return 0;
    }
}
