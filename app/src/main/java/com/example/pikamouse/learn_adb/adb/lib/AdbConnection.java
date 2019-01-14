package com.example.pikamouse.learn_adb.adb.lib;

import android.util.Log;

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
    private final static String TAG = "AdbConnection";

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
                                    if (msg.command == 1163086915) Log.d(TAG, "A_CLSE");
                                    if (msg.command == 1163154007) Log.d(TAG, "A_WRTE");
                                    if (msg.command == 1497451343) Log.d(TAG, "A_OKAY");
                                    if (!AdbConnection.this.connected) {
                                        continue;
                                    }
                                    // A READY message containing a remote-id which does not map to an open stream on the recipient's side is ignored.
                                    // A WRITE message containing a remote-id which does not map to an open stream on the recipient's side is ignored.
                                    // A CLOSE message containing a remote-id which does not map to an open stream on the recipient's side is ignored.
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
                                    Log.d(TAG, "A_AUTH");
                                    //If type is TOKEN(1), data is a random token that the recipient can sign with a private key.
                                    if (msg.arg0 != 1) {
                                        continue;
                                    }

                                    byte[] packet;
                                    //Once the recipient has tried all its private keys, it can reply with an
                                    //AUTH packet where type is RSAPUBLICKEY(3) and data is the public key.
                                    if (AdbConnection.this.sentSignature) {
                                        packet = AdbProtocol.generateAuth(3, AdbConnection.this.crypto.getAdbPublicKeyPayload());
                                    } else {
                                        //The recipient replies with an AUTH packet where type is SIGNATURE(2) and data is the signature.
                                        packet = AdbProtocol.generateAuth(2, AdbConnection.this.crypto.signAdbTokenPayload(msg.payload));
                                        AdbConnection.this.sentSignature = true;
                                    }

                                    AdbConnection.this.outputStream.write(packet);
                                    AdbConnection.this.outputStream.flush();
                                    continue;
                                case 1314410051://A_CNXN
                                    Log.d(TAG, "A_CNXN");
                                    AdbConnection var4 = AdbConnection.this;
                                    synchronized(AdbConnection.this) {
                                        AdbConnection.this.maxData = msg.arg1;
                                        AdbConnection.this.connected = true;
                                        //notify main thread
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
                    //wait for the connection
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
            //write connect command
            //Both sides send a CONNECT message when the connection between them is established.
            this.outputStream.write(AdbProtocol.generateConnect());
            this.outputStream.flush();
            this.connectAttempted = true;
            this.connectionThread.start();
            synchronized(this) {
                if (!this.connected) {
                    //Main thread wait for the connection
                    this.wait();
                }

                if (!this.connected) {
                    throw new IOException("Connection failed");
                }
            }
        }
    }

    public AdbStream open(String destination) throws UnsupportedEncodingException, IOException, InterruptedException {
        int localId = ++this.lastLocalId;//The local-id may not be zero.
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
            //write open command
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

