package com.mentalresonance.dust.core.net;

import com.mentalresonance.dust.core.actors.ActorRef;
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

    // Header now contains: PayloadSize (4) + srcId (4) + targetId (4) = 12 bytes
    private static final int HEADER_SIZE = 12;
    private static final int DEFAULT_CAPACITY = 8 * 1024;
    private static final int MAX_FRAME_SIZE = 8 * 1024 * 1024;

    private final Fory fory;
    private ByteBuffer buffer;
    private MemoryBuffer mem;

    @Getter @Setter private int srcId;
    @Getter @Setter private int targetId;

    @Getter
    private SocketChannel socketChannel;

    /**
     * Returns srcId and targetId combined into a single long.
     * High 32 bits: srcId, Low 32 bits: targetId.
     */
    public long getCombinedIds() {
        return (((long) srcId) << 32) | (targetId & 0xFFFFFFFFL);
    }

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
            } catch (IOException e) {
                log.error("Failed to configure SocketChannel", e);
            }
        }
        return this;
    }

    public void send(Object obj) throws Exception {
        if (obj == null) {
            ensureCapacity(HEADER_SIZE);
            buffer.clear();
            buffer.putInt(0);          // Payload size 0
            buffer.putInt(srcId);     // Add IDs even for null objects
            buffer.putInt(targetId);
            buffer.flip();
            writeFully(buffer);
            return;
        }

        while (true) {

            try {
                buffer.clear();
                // Start writing payload after the full 12-byte header
                buffer.position(HEADER_SIZE);
                mem.readerIndex(0);
                mem.writerIndex(HEADER_SIZE);
                fory.serialize(mem, obj);

                int end = mem.writerIndex();
                int payloadSize = end - HEADER_SIZE;

                if (payloadSize < 0 || payloadSize + HEADER_SIZE > MAX_FRAME_SIZE) {
                    throw new RemotePayloadSizeException("Serialized payload size " + payloadSize + " exceeds max frame size: " + MAX_FRAME_SIZE);
                }

                // Write header fields at absolute positions
                buffer.putInt(0, payloadSize);
                buffer.putInt(4, srcId);
                buffer.putInt(8, targetId);

                buffer.limit(end);
                buffer.position(0);

                writeFully(buffer);
                // log.info("Sent {} bytes (Payload: {}) for {}", end, payloadSize, obj);
                return;
            } catch (IndexOutOfBoundsException | BufferOverflowException | IllegalArgumentException e) {
                ByteBuffer newBuffer = ByteBuffer.allocateDirect(2*buffer.capacity());
                this.buffer = newBuffer;
                this.mem = MemoryUtils.wrap(newBuffer);
            }
        }
    }

    /**
     * Reads only the header from the wire.
     * Updates srcId, targetId, and returns the expected payload size.
     * Use this to inspect IDs before committing to a full object read.
     * * @return The size of the following payload in bytes.
     * @throws IOException if the connection is closed or protocol is violated.
     */
    public int readHeader() throws IOException {
        ensureCapacity(HEADER_SIZE);

        buffer.clear();
        buffer.limit(HEADER_SIZE);

        // Read exactly 12 bytes: [PayloadSize(4)][srcId(4)][targetId(4)]
        readFully(buffer);
        buffer.flip();

        int payloadSize = buffer.getInt();
        this.srcId = buffer.getInt();
        this.targetId = buffer.getInt();

        if (payloadSize < 0 || payloadSize > MAX_FRAME_SIZE) {
            throw new IOException("Protocol violation: invalid payload size " + payloadSize);
        }

        return payloadSize;
    }

    /**
     * Read *rest* of payload. So sequence must be readHeader() once then use its returned payloadSize
     * @param payloadSize
     * @return
     * @throws Exception
     */
    public Object receivePayload(int payloadSize) throws Exception {
        if (payloadSize == 0) return null;

        ensureCapacity(payloadSize);
        buffer.clear();
        buffer.limit(payloadSize);
        readFully(buffer);
        buffer.flip();

        mem.readerIndex(0);
        mem.writerIndex(payloadSize);

        return fory.deserialize(mem);
    }

    /**
     * Is the sender of this message null? Must be called after readHeader()
     * @return
     */
    public boolean isNullSender() {
        return srcId == ActorRef.NullActorRefID;
    }

    private void ensureCapacity(int neededBytes) {
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