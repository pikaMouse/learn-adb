package com.example.pikamouse.learn_adb.adb.lib;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;

/**
 * @author: jiangfeng
 * @date: 2019/1/8
 */
public class AdbConnection implements Closeable {
    private Socket socket;
    private int lastLocalId = 0;
    private InputStream inputStream;
    OutputStream outputStream;
    private Thread connectionThread = this.createConnectionThread();
    private boolean connectAttempted;
    private boolean connected;
    private int maxData;
    private AdbCrypto crypto;
    private boolean sentSignature;
    private HashMap<Integer, AdbStream> openStreams = new HashMap();

    private AdbConnection() {
    }

    public static AdbConnection create(Socket socket, AdbCrypto crypto) throws IOException {
        AdbConnection newConn = new AdbConnection();
        newConn.crypto = crypto;
        newConn.socket = socket;
        newConn.inputStream = socket.getInputStream();
        newConn.outputStream = socket.getOutputStream();
        socket.setTcpNoDelay(true);
        return newConn;
    }

    private Thread createConnectionThread() {
        return new Thread(new Runnable() {
            public void run() {
                while(true) {
                    if (!AdbConnection.this.connectionThread.isInterrupted()) {
                        try {
                            AdbProtocol.AdbMessage msg = AdbProtocol.AdbMessage.parseAdbMessage(AdbConnection.this.inputStream);
                            if (!AdbProtocol.validateMessage(msg)) {
                                continue;
                            }

                            switch(msg.command) {
                                case 1163086915://A_CLSE
                                case 1163154007://A_WRTE
                                case 1497451343://A_OKAY
                                    if (!AdbConnection.this.connected) {
                                        continue;
                                    }

                                    AdbStream waitingStream = (AdbStream)AdbConnection.this.openStreams.get(msg.arg1);
                                    if (waitingStream == null) {
                                        continue;
                                    }

                                    synchronized(waitingStream) {
                                        if (msg.command == 1497451343) {//A_OKAY
                                            waitingStream.updateRemoteId(msg.arg0);
                                            waitingStream.readyForWrite();
                                            waitingStream.notify();
                                        } else if (msg.command == 1163154007) {//A_WRTE
                                            waitingStream.addPayload(msg.payload);
                                            waitingStream.sendReady();
                                        } else if (msg.command == 1163086915) {//A_CLSE
                                            AdbConnection.this.openStreams.remove(msg.arg1);
                                            waitingStream.notifyClose();
                                        }
                                        continue;
                                    }
                                case 1213486401://A_AUTH
                                    if (msg.arg0 != 1) {
                                        continue;
                                    }

                                    byte[] packet;
                                    //Once the recipient has tried all its private keys, it can reply with an
                                    //AUTH packet where type is RSAPUBLICKEY(3) and data is the public key.
                                    if (AdbConnection.this.sentSignature) {
                                        packet = AdbProtocol.generateAuth(3, AdbConnection.this.crypto.getAdbPublicKeyPayload());
                                    } else {
                                        //If type is TOKEN(1), data is a random token that
                                        //the recipient can sign with a private key. The recipient replies with an
                                        //AUTH packet where type is SIGNATURE(2) and data is the signature.
                                        packet = AdbProtocol.generateAuth(2, AdbConnection.this.crypto.signAdbTokenPayload(msg.payload));
                                        AdbConnection.this.sentSignature = true;
                                    }

                                    AdbConnection.this.outputStream.write(packet);
                                    AdbConnection.this.outputStream.flush();
                                    continue;
                                case 1314410051://A_CNXN
                                    AdbConnection var4 = AdbConnection.this;
                                    synchronized(AdbConnection.this) {
                                        AdbConnection.this.maxData = msg.arg1;
                                        AdbConnection.this.connected = true;
                                        AdbConnection.this.notifyAll();
                                    }
                                default:
                                    continue;
                            }
                        } catch (Exception var8) {
                            ;
                        }
                    }

                    AdbConnection var1 = AdbConnection.this;
                    synchronized(AdbConnection.this) {
                        AdbConnection.this.cleanupStreams();
                        AdbConnection.this.notifyAll();
                        AdbConnection.this.connectAttempted = false;
                        return;
                    }
                }
            }
        });
    }

    public int getMaxData() throws InterruptedException, IOException {
        if (!this.connectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        } else {
            synchronized(this) {
                if (!this.connected) {
                    this.wait();
                }

                if (!this.connected) {
                    throw new IOException("Connection failed");
                }
            }

            return this.maxData;
        }
    }

    public void connect() throws IOException, InterruptedException {
        if (this.connected) {
            throw new IllegalStateException("Already connected");
        } else {
            this.outputStream.write(AdbProtocol.generateConnect());
            this.outputStream.flush();
            this.connectAttempted = true;
            this.connectionThread.start();
            synchronized(this) {
                if (!this.connected) {
                    this.wait();
                }

                if (!this.connected) {
                    throw new IOException("Connection failed");
                }
            }
        }
    }

    public AdbStream open(String destination) throws UnsupportedEncodingException, IOException, InterruptedException {
        int localId = ++this.lastLocalId;
        if (!this.connectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        } else {
            synchronized(this) {
                if (!this.connected) {
                    this.wait();
                }

                if (!this.connected) {
                    throw new IOException("Connection failed");
                }
            }

            AdbStream stream = new AdbStream(this, localId);
            this.openStreams.put(localId, stream);
            this.outputStream.write(AdbProtocol.generateOpen(localId, destination));
            this.outputStream.flush();
            synchronized(stream) {
                stream.wait();
            }

            if (stream.isClosed()) {
                throw new ConnectException("Stream open actively rejected by remote peer");
            } else {
                return stream;
            }
        }
    }

    private void cleanupStreams() {
        Iterator var2 = this.openStreams.values().iterator();

        while(var2.hasNext()) {
            AdbStream s = (AdbStream)var2.next();

            try {
                s.close();
            } catch (IOException var4) {
                ;
            }
        }

        this.openStreams.clear();
    }

    public void close() throws IOException {
        if (this.connectionThread != null) {
            this.socket.close();
            this.connectionThread.interrupt();
            //优先执行connectionThread线程中的方法
            try {
                this.connectionThread.join();
            } catch (InterruptedException var2) {
                ;
            }

        }
    }
}

