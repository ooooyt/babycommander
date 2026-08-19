package com.ooooyt.babycommander.ui;

import com.ooooyt.babycommander.status.StatusEventPublisher;
import com.ooooyt.babycommander.ui.tui.TerminalUIAdapter;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class UiAdapterProducer {

    @Inject
    EventBus eventBus;

    @Inject
    StatusEventPublisher statusEventPublisher;

    private static volatile TerminalUIAdapter preCreated;

    @PostConstruct
    void wirePreCreated() {
        TerminalUIAdapter adapter = preCreated;
        if (adapter != null) {
            adapter.wire(eventBus, statusEventPublisher);
        }
    }

    public static void setPreCreated(TerminalUIAdapter adapter) {
        UiAdapterProducer.preCreated = adapter;
    }

    @Produces
    @ApplicationScoped
    public UiAdapter produceUiAdapter() {
        if (preCreated != null) {
            return preCreated;
        }
        return new TerminalUIAdapter(eventBus, statusEventPublisher);
    }
}
