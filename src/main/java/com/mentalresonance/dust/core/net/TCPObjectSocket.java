package com.mentalresonance.dust.core.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;

@Slf4j
public class TCPObjectSocket {

    // Unique protocol identifier: 'FORY'
    private static final int MAGIC_NUMBER = 0x464F5259;
    private static final int MAX_PAYLOAD = 64 * 1024;
    private static final int HEADER_SIZE = 8; // 4 bytes Magic + 4 bytes Length

    private final Fory fory;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private final MemoryBuffer readMem;
    private final MemoryBuffer writeMem;

    @Getter
    private SocketChannel socketChannel;

    /**
     * Constructor for pre-allocating in a pool.
     */
    public TCPObjectSocket() {
        this.fory = ForyService.fory();

        // Using Direct Buffers for better NIO performance
        this.readBuffer = ByteBuffer.allocateDirect(MAX_PAYLOAD + HEADER_SIZE);
        this.writeBuffer = ByteBuffer.allocateDirect(MAX_PAYLOAD + HEADER_SIZE);

        this.readMem = MemoryUtils.wrap(readBuffer);
        this.writeMem = MemoryUtils.wrap(writeBuffer);
    }

    /**
     * Constructor for immediate wrapping.
     */
    public TCPObjectSocket(SocketChannel ch) {
        this();
        wrap(ch);
    }

    /**
     * Prepares the instance for reuse in a pool.
     */
    public void init() {
        readBuffer.clear();
        writeBuffer.clear();
        // Reset Fory indices
        readMem.readerIndex(0);
        readMem.writerIndex(0);
        writeMem.readerIndex(0);
        writeMem.writerIndex(0);
    }

    public TCPObjectSocket wrap(SocketChannel ch) {
        try {
            this.socketChannel = ch;
            // Ensure blocking mode for this specific implementation
            this.socketChannel.configureBlocking(true);
            // Disable Nagle's algorithm for lower latency in object exchange
            this.socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            return this;
        }
        catch (IOException e) {
            log.error("Failed to wrap socket channel: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------
    // SEND
    // ------------------------------------------------

    public void send(Object obj) throws Exception {
        writeBuffer.clear();

        // Leave space for the Header (Magic + Size)
        writeBuffer.position(HEADER_SIZE);
        writeMem.writerIndex(HEADER_SIZE);

        // Serialize the object into the buffer starting at index 8
        fory.serialize(writeMem, obj);

        int endPosition = writeMem.writerIndex();
        int payloadSize = endPosition - HEADER_SIZE;

        // Write headers at the very beginning
        writeBuffer.putInt(0, MAGIC_NUMBER);
        writeBuffer.putInt(4, payloadSize);

        // Prepare buffer for the channel write
        writeBuffer.limit(endPosition);
        writeBuffer.position(0);

        while (writeBuffer.hasRemaining()) {
            socketChannel.write(writeBuffer);
        }
    }

    // ------------------------------------------------
    // RECEIVE
    // ------------------------------------------------

    public Object receive() throws Exception {
        // 1. Read the 8-byte Header
        readFully(HEADER_SIZE);
        readBuffer.flip();

        int magic = readBuffer.getInt();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Protocol mismatch! Expected Magic " +
                Integer.toHexString(MAGIC_NUMBER) + " but got " + Integer.toHexString(magic));
        }

        int payloadSize = readBuffer.getInt();
        if (payloadSize < 0 || payloadSize > MAX_PAYLOAD) {
            throw new IOException("Invalid payload size: " + payloadSize);
        }

        // 2. Read the payload
        readFully(payloadSize);
        readBuffer.flip();

        // 3. Sync Fory MemoryBuffer
        readMem.readerIndex(0);
        readMem.writerIndex(payloadSize);

        return fory.deserialize(readMem);
    }

    // ------------------------------------------------
    // INTERNAL UTILS
    // ------------------------------------------------

    private void readFully(int size) throws Exception {
        readBuffer.clear();
        readBuffer.limit(size); // CRITICAL: hasRemaining() depends on this limit

        while (readBuffer.hasRemaining()) {
            int n = socketChannel.read(readBuffer);

            if (n == -1) {
                throw new EOFException("Socket closed by peer while expecting " + size + " bytes");
            }

            if (n == 0) {
                Thread.onSpinWait();
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