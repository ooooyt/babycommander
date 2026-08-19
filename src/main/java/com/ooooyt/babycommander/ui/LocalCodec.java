package com.ooooyt.babycommander.ui;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

public class LocalCodec<T> implements MessageCodec<T, T> {
    private final String name;

    public LocalCodec(Class<T> type) {
        this.name = type.getName();
    }

    @Override
    public void encodeToWire(Buffer buffer, T t) {
        throw new UnsupportedOperationException("Local codec does not support wire transfer");
    }

    @Override
    public T decodeFromWire(int i, Buffer buffer) {
        throw new UnsupportedOperationException("Local codec does not support wire transfer");
    }

    @Override
    public T transform(T t) {
        return t;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
