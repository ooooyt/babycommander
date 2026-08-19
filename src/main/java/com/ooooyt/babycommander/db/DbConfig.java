package com.ooooyt.babycommander.db;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "db")
public interface DbConfig {

    Memory memory();

    @WithDefault("true")
    boolean enabled();

    @WithDefault("objectbox-data")
    String directory();

    interface Memory {
        @WithDefault("200")
        int maxMessages();

        @WithDefault("80")
        int maxToolCalls();

        @WithDefault("20")
        int summaryBatchSize();
    }
}
