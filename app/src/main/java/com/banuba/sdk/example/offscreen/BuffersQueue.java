package com.banuba.sdk.example.offscreen;

import androidx.annotation.NonNull;

import com.banuba.sdk.offscreen.BufferAllocator;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class BuffersQueue implements BufferAllocator {
    private final int capacity;
    private final Queue<ByteBuffer> queue = new LinkedList<>();

    public BuffersQueue(int capacity) {
        this.capacity = capacity;
    }

    public BuffersQueue() {
        this(4);
    }

    @NonNull
    @Override
    public synchronized ByteBuffer allocateBuffer(int minimumCapacity) {
        final ByteBuffer buffer = queue.poll();
        if (buffer != null && buffer.capacity() >= minimumCapacity) {
            buffer.rewind();
            return buffer;
        }

        return ByteBuffer.allocateDirect(minimumCapacity);
    }

    public synchronized void retainBuffer(@NonNull ByteBuffer buffer) {
        if (queue.size() < capacity) {
            queue.add(buffer);
        }
    }
}
