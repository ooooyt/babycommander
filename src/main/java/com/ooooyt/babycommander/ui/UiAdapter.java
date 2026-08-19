package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.hook.ConfirmationHandler;

public interface UiAdapter {

    default void start() {}

    default void stop() {}

    default void waitUntilStopped() {}

    void publishCommand(CommandEvent command);

    ConfirmationHandler getConfirmationHandler();
}
