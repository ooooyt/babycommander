package com.ooooyt.babycommander.ui;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalCodecTest {

    @Test
    void testName() {
        LocalCodec<String> codec = new LocalCodec<>(String.class);
        assertEquals(String.class.getName(), codec.name());
    }

    @Test
    void testSystemCodecID() {
        LocalCodec<String> codec = new LocalCodec<>(String.class);
        assertEquals(-1, codec.systemCodecID());
    }

    @Test
    void testTransform() {
        LocalCodec<String> codec = new LocalCodec<>(String.class);
        assertEquals("hello", codec.transform("hello"));
        assertNull(codec.transform(null));
    }

    @Test
    void testEncodeToWireThrows() {
        LocalCodec<String> codec = new LocalCodec<>(String.class);
        assertThrows(UnsupportedOperationException.class, () ->
            codec.encodeToWire(Buffer.buffer(), "test"));
    }

    @Test
    void testDecodeFromWireThrows() {
        LocalCodec<String> codec = new LocalCodec<>(String.class);
        assertThrows(UnsupportedOperationException.class, () ->
            codec.decodeFromWire(0, Buffer.buffer()));
    }

    @Test
    void testNameForDifferentType() {
        LocalCodec<Integer> codec = new LocalCodec<>(Integer.class);
        assertEquals(Integer.class.getName(), codec.name());
    }
}
