package com.ooooyt.babycommander.db;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "summarizer")
public interface SummarizerConfig {

    @WithDefault("default")
    String provider();

    @WithDefault("")
    String modelName();

    @WithDefault("0.3")
    double temperature();
}
