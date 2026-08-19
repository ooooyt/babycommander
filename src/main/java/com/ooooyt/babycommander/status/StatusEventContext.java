package com.ooooyt.babycommander.status;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class StatusEventContext {

    private static final ThreadLocal<StatusEventPublisher> CURRENT = new ThreadLocal<>();

    private final StatusEventPublisher publisher;

    @Inject
    public StatusEventContext(StatusEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void activate() {
        CURRENT.set(publisher);
    }

    public void deactivate() {
        CURRENT.remove();
    }

    public static StatusEventPublisher get() {
        return CURRENT.get();
    }
}
