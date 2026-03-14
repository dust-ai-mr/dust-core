package com.mentalresonance.dust.core.net;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

@Slf4j
public class TCPObjectSocket {

    private static final int HEADER_SIZE = 4;
    private static final int DEFAULT_CAPACITY = 64 * 1024 + HEADER_SIZE;
    private static final int MAX_FRAME_SIZE = 8 * 1024 * 1024; // example 64 MiB hard cap

    private final Fory fory;

    private ByteBuffer buffer;
    private MemoryBuffer mem;

    @Getter
    private SocketChannel socketChannel;

    public TCPObjectSocket() {
        this.fory = ForyService.fory();
        this.buffer = ByteBuffer.allocateDirect(DEFAULT_CAPACITY);
        this.mem = MemoryUtils.wrap(buffer);
    }

    public TCPObjectSocket(SocketChannel ch) {
        this();
        wrap(ch);
    }

    public void init() {
        buffer.clear();
        mem.readerIndex(0);
        mem.writerIndex(0);
    }

    public TCPObjectSocket wrap(SocketChannel ch) {
        if (ch != null) {
            try {
                this.socketChannel = ch;
                this.socketChannel.configureBlocking(true);
                this.socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                this.socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                return this;
            }
            catch (IOException e) {
                log.error("Failed to configure SocketChannel", e);
            }
        }
        return this;
    }

    public void send(Object obj) throws Exception {
        if (obj == null) {
            ensureCapacity(HEADER_SIZE);
            buffer.clear();
            buffer.putInt(0);
            buffer.flip();
            writeFully(buffer);
            return;
        }

        // First try with current buffer.
        while (true) {
            buffer.clear();
            buffer.position(HEADER_SIZE);
            mem.readerIndex(0);
            mem.writerIndex(HEADER_SIZE);

            try {
                fory.serialize(mem, obj);

                int end = mem.writerIndex();
                int payloadSize = end - HEADER_SIZE;

                if (payloadSize < 0 || payloadSize > MAX_FRAME_SIZE) {
                    throw new IOException("Serialized payload exceeds max frame size: " + payloadSize);
                }

                buffer.putInt(0, payloadSize);
                buffer.limit(end);
                buffer.position(0);

                writeFully(buffer);
                log.trace("Sent {} bytes for {}", end, obj);
                return;
            } catch (IndexOutOfBoundsException | BufferOverflowException | IllegalArgumentException e) {
                // Buffer too small; grow and retry.
                int needed = Math.max(buffer.capacity() * 2, estimateNeededCapacity());
                ensureCapacity(Math.min(needed, MAX_FRAME_SIZE + HEADER_SIZE));
            }
        }
    }

    public Object receive() throws Exception {
        ensureCapacity(HEADER_SIZE);

        buffer.clear();
        buffer.limit(HEADER_SIZE);
        readFully(buffer);
        buffer.flip();

        int payloadSize = buffer.getInt();

        if (payloadSize == 0) {
            return null;
        }
        if (payloadSize < 0 || payloadSize > MAX_FRAME_SIZE) {
            throw new IOException("Protocol violation: invalid payload size " + payloadSize);
        }

        ensureCapacity(payloadSize);

        buffer.clear();
        buffer.limit(payloadSize);
        readFully(buffer);
        buffer.flip();

        mem.readerIndex(0);
        mem.writerIndex(payloadSize);

        try {
            Object result = fory.deserialize(mem);
            return result;
        }
        catch (Exception e) {
            log.error("Failed to deserialize {} size {}", e, payloadSize);
            throw e;
        }
    }

    private void ensureCapacity(int neededPayloadBytes) {
        int needed = neededPayloadBytes;
        if (needed > MAX_FRAME_SIZE + HEADER_SIZE) {
            throw new IllegalArgumentException("Requested capacity exceeds hard cap: " + needed);
        }
        if (buffer.capacity() >= needed) {
            return;
        }

        int newCapacity = nextPowerOfTwo(needed);
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
        this.buffer = newBuffer;
        this.mem = MemoryUtils.wrap(newBuffer);
    }

    private int estimateNeededCapacity() {
        // crude retry growth target when serialization overflowed
        return Math.max(buffer.capacity() * 2, HEADER_SIZE + 1024);
    }

    private static int nextPowerOfTwo(int x) {
        int n = 1;
        while (n < x) {
            n <<= 1;
        }
        return n;
    }

    private void readFully(ByteBuffer dst) throws IOException {
        while (dst.hasRemaining()) {
            int n = socketChannel.read(dst);
            if (n == -1) {
                throw new EOFException("Socket closed while reading " + dst.limit() + " bytes");
            }
        }
    }

    private void writeFully(ByteBuffer src) throws IOException {
        while (src.hasRemaining()) {
            socketChannel.write(src);
        }
    }

    public void close() {
        try {
            if (socketChannel != null) {
                socketChannel.close();
            }
        } catch (IOException ignored) {
        } finally {
            socketChannel = null;
        }
    }

    public boolean isClosed() {
        return socketChannel == null || !socketChannel.isOpen();
    }

    public void restoreInitialCapacity() {
        if (buffer.capacity() != DEFAULT_CAPACITY) {
            buffer = ByteBuffer.allocateDirect(DEFAULT_CAPACITY);
            mem = MemoryUtils.wrap(buffer);
        }

        buffer.clear();
        mem.readerIndex(0);
        mem.writerIndex(0);
    }
}