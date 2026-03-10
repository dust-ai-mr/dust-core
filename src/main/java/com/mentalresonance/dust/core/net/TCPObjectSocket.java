package com.mentalresonance.dust.core.net;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * High-performance TCP Wrapper for Fory Serialization.
 * Optimized for Virtual Threads and Sequential Request/Ack patterns.
 */
@Slf4j
public class TCPObjectSocket {

    private static final int MAX_PAYLOAD = 64 * 1024;
    private static final int HEADER_SIZE = 4; // 4 bytes for Length Int

    private final Fory fory;

    // Shared buffer for both directions (Saves 64KB native memory per socket)
    private final ByteBuffer buffer;
    private final MemoryBuffer mem;

    @Getter
    private SocketChannel socketChannel;

    public TCPObjectSocket() {
        this.fory = ForyService.fory();

        // Single allocation of Direct Memory (Outside JVM Heap)
        this.buffer = ByteBuffer.allocateDirect(MAX_PAYLOAD + HEADER_SIZE);
        this.mem = MemoryUtils.wrap(buffer);
    }

    public TCPObjectSocket(SocketChannel ch) {
        this();
        wrap(ch);
    }

    /**
     * Resets buffer pointers. Call this before returning to a pool.
     */
    public void init() {
        buffer.clear();
        mem.readerIndex(0);
        mem.writerIndex(0);
    }

    /**
     * Configures the channel for high-throughput, low-latency object transfer.
     */
    public TCPObjectSocket wrap(SocketChannel ch) {
        if (ch != null) {
            try {
                this.socketChannel = ch;
                this.socketChannel.configureBlocking(true);

                // Disable Nagle's algorithm for instant ACK delivery
                this.socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                this.socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                this.socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, MAX_PAYLOAD + HEADER_SIZE);
                this.socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, MAX_PAYLOAD + HEADER_SIZE);

                return this;
            } catch (IOException e) {
                log.error("Failed to configure SocketChannel: {}", e.getMessage());
            }
        }
        return this;
    }

    // ------------------------------------------------
    // SEND
    // ------------------------------------------------

    public void send(Object obj) throws Exception {
        buffer.clear();

        if (obj == null) {
            // Fast Null/Ack: Just 4 bytes of 0
            buffer.putInt(0);
            buffer.flip();
        }
        else {
            // 1. Reserve space for header, start writing at index 4
            buffer.position(HEADER_SIZE);
            mem.writerIndex(HEADER_SIZE);

            // 2. Serialize directly into the Direct Buffer
            fory.serialize(mem, obj);

            int endPosition = mem.writerIndex();
            int payloadSize = endPosition - HEADER_SIZE;

            // 3. Patch the length header at the beginning
            buffer.putInt(0, payloadSize);

            // 4. Prepare for the syscall
            buffer.limit(endPosition);
            buffer.position(0);
        }

        // Loop handles partial writes (though rare in blocking mode)
        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }
    }

    // ------------------------------------------------
    // RECEIVE
    // ------------------------------------------------

    public Object receive() throws Exception {
        // 1. Read the 4-byte header
        fillBuffer(HEADER_SIZE);
        buffer.flip();
        int payloadSize = buffer.getInt();

        // 0-byte payload indicates a NULL object/ACK
        if (payloadSize == 0) {
            return null;
        }

        if (payloadSize < 0 || payloadSize > MAX_PAYLOAD) {
            throw new IOException("Protocol violation: Invalid payload size " + payloadSize);
        }

        // 2. Read the full payload
        fillBuffer(payloadSize);
        buffer.flip();

        // 3. Map Fory MemoryBuffer to the data segment
        mem.readerIndex(0);
        mem.writerIndex(payloadSize);

        // 4. Reconstruct the object
        return fory.deserialize(mem);
    }

    // ------------------------------------------------
    // INTERNAL UTILS
    // ------------------------------------------------

    /**
     * Blocks until exactly 'size' bytes are loaded into the buffer.
     */
    private void fillBuffer(int size) throws IOException {
        buffer.clear();
        buffer.limit(size);

        while (buffer.hasRemaining()) {
            int n = socketChannel.read(buffer);

            if (n == -1) {
                throw new EOFException("Socket closed while expecting " + size + " bytes");
            }
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
}