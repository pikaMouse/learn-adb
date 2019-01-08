package com.example.pikamouse.learn_adb.adb.lib;

import java.io.Closeable;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: jiangfeng
 * @date: 2019/1/8
 */
public class AdbStream implements Closeable {
    private AdbConnection adbConn;
    private int localId;
    private int remoteId;
    private AtomicBoolean writeReady;
    private Queue<byte[]> readQueue;
    private boolean isClosed;

    public AdbStream(AdbConnection adbConn, int localId) {
        this.adbConn = adbConn;
        this.localId = localId;
        this.readQueue = new ConcurrentLinkedQueue();
        this.writeReady = new AtomicBoolean(false);
        this.isClosed = false;
    }

    void addPayload(byte[] payload) {
        Queue var2 = this.readQueue;
        synchronized(this.readQueue) {
            this.readQueue.add(payload);
            this.readQueue.notifyAll();
        }
    }

    void sendReady() throws IOException {
        byte[] packet = AdbProtocol.generateReady(this.localId, this.remoteId);
        this.adbConn.outputStream.write(packet);
        this.adbConn.outputStream.flush();
    }

    void updateRemoteId(int remoteId) {
        this.remoteId = remoteId;
    }

    void readyForWrite() {
        this.writeReady.set(true);
    }

    void notifyClose() {
        this.isClosed = true;
        synchronized(this) {
            this.notifyAll();
        }

        Queue var1 = this.readQueue;
        synchronized(this.readQueue) {
            this.readQueue.notifyAll();
        }
    }

    public byte[] read() throws InterruptedException, IOException {
        byte[] data = null;
        Queue var2 = this.readQueue;
        synchronized(this.readQueue) {
            while(!this.isClosed && (data = (byte[])this.readQueue.poll()) == null) {
                this.readQueue.wait();
            }

            if (this.isClosed) {
                throw new IOException("Stream closed");
            } else {
                return data;
            }
        }
    }

    public void write(String payload) throws IOException, InterruptedException {
        this.write(payload.getBytes("UTF-8"), false);
        this.write(new byte[1], true);
    }

    public void write(byte[] payload) throws IOException, InterruptedException {
        this.write(payload, true);
    }

    public void write(byte[] payload, boolean flush) throws IOException, InterruptedException {
        synchronized(this) {
            while(!this.isClosed && !this.writeReady.compareAndSet(true, false)) {
                this.wait();
            }

            if (this.isClosed) {
                throw new IOException("Stream closed");
            }
        }

        byte[] packet = AdbProtocol.generateWrite(this.localId, this.remoteId, payload);
        this.adbConn.outputStream.write(packet);
        if (flush) {
            this.adbConn.outputStream.flush();
        }

    }

    public void close() throws IOException {
        synchronized(this) {
            if (this.isClosed) {
                return;
            }

            this.notifyClose();
        }

        byte[] packet = AdbProtocol.generateClose(this.localId, this.remoteId);
        this.adbConn.outputStream.write(packet);
        this.adbConn.outputStream.flush();
    }

    public boolean isClosed() {
        return this.isClosed;
    }
}

