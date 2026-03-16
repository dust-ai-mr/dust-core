package com.mentalresonance.dust.core.net;

import lombok.Getter;
import lombok.Setter;
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

    /** 4 bytes for payload size + 4 bytes for actorRefId */
    private static final int HEADER_SIZE = 8;
    private static final int DEFAULT_CAPACITY = 64 * 1024 + HEADER_SIZE;
    private static final int MAX_FRAME_SIZE = 8 * 1024 * 1024;

    private final Fory fory;

    @Getter
    @Setter
    private int actorRefId = 0; // Now a setter-driven field

    private ByteBuffer buffer;
    private MemoryBuffer mem;

    @Getter
    private SocketChannel socketChannel;

    // Internal state to track if we've already consumed the header from the wire
    private boolean headerRead = false;
    private int lastReadPayloadSize = 0;
    private int lastReadTypeId = 0;

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
        headerRead = false;
    }

    public TCPObjectSocket wrap(SocketChannel ch) {
        if (ch != null) {
            try {
                this.socketChannel = ch;
                this.socketChannel.configureBlocking(true);
                this.socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                this.socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                return this;
            } catch (IOException e) {
                log.error("Failed to configure SocketChannel", e);
            }
        }
        return this;
    }

    /**
     * Reads only the 8-byte header from the channel.
     * Useful for checking message types before committing to a full payload read.
     */
    public int actorRefId() throws IOException {
        if (headerRead) {
            return lastReadTypeId;
        }

        ensureCapacity(HEADER_SIZE);
        buffer.clear();
        buffer.limit(HEADER_SIZE);

        readFully(buffer);
        buffer.flip();

        lastReadPayloadSize = buffer.getInt();
        lastReadTypeId = buffer.getInt();
        headerRead = true;

        return lastReadTypeId;
    }

    public void send(Object obj) throws Exception {
        if (obj == null) {
            ensureCapacity(HEADER_SIZE);
            buffer.clear();
            buffer.putInt(0);       // Payload Size
            buffer.putInt(actorRefId);  // Type ID
            buffer.flip();
            writeFully(buffer);
            return;
        }

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

                // Fill header using absolute puts to avoid messing with position
                buffer.putInt(0, payloadSize);
                buffer.putInt(4, actorRefId);

                buffer.limit(end);
                buffer.position(0);

                writeFully(buffer);
                log.trace("Sent {} bytes (actorRefId: {}) for {}", end, actorRefId, obj);
                return;
            } catch (IndexOutOfBoundsException | BufferOverflowException | IllegalArgumentException e) {
                int needed = Math.max(buffer.capacity() * 2, estimateNeededCapacity());
                ensureCapacity(Math.min(needed, MAX_FRAME_SIZE + HEADER_SIZE));
            }
        }
    }

    public Object receive() throws Exception {
        int payloadSize;
        int receivedTypeId;

        if (headerRead) {
            payloadSize = lastReadPayloadSize;
            receivedTypeId = lastReadTypeId;
            headerRead = false; // Reset state for next message
        } else {
            ensureCapacity(HEADER_SIZE);
            buffer.clear();
            buffer.limit(HEADER_SIZE);
            readFully(buffer);
            buffer.flip();
            payloadSize = buffer.getInt();
            receivedTypeId = buffer.getInt();
        }

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
            return fory.deserialize(mem);
        } catch (Exception e) {
            log.error("Failed to deserialize type {} size {}", receivedTypeId, payloadSize);
            throw e;
        }
    }

    private void ensureCapacity(int neededBytes) {
        if (neededBytes > MAX_FRAME_SIZE + HEADER_SIZE) {
            throw new IllegalArgumentException("Requested capacity exceeds hard cap: " + neededBytes);
        }
        if (buffer.capacity() >= neededBytes) {
            return;
        }

        int newCapacity = nextPowerOfTwo(neededBytes);
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
        this.buffer = newBuffer;
        this.mem = MemoryUtils.wrap(newBuffer);
    }

    private int estimateNeededCapacity() {
        return Math.max(buffer.capacity() * 2, HEADER_SIZE + 1024);
    }

    private static int nextPowerOfTwo(int x) {
        int n = 1;
        while (n < x) n <<= 1;
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
        init();
    }
}