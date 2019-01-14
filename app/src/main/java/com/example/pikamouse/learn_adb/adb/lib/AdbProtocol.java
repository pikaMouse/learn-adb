package com.example.pikamouse.learn_adb.adb.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author: jiangfeng
 * @date: 2019/1/8
 */
public class AdbProtocol {
    public static final int ADB_HEADER_LENGTH = 24;
    public static final int CMD_SYNC = 1129208147;
    public static final int CMD_CNXN = 1314410051;
    public static final int CONNECT_VERSION = 16777216;
    public static final int CONNECT_MAXDATA = 4096;
    public static byte[] CONNECT_PAYLOAD;
    public static final int CMD_AUTH = 1213486401;
    public static final int AUTH_TYPE_TOKEN = 1;
    public static final int AUTH_TYPE_SIGNATURE = 2;
    public static final int AUTH_TYPE_RSA_PUBLIC = 3;
    public static final int CMD_OPEN = 1313165391;
    public static final int CMD_OKAY = 1497451343;
    public static final int CMD_CLSE = 1163086915;
    public static final int CMD_WRTE = 1163154007;

    static {
        try {
            CONNECT_PAYLOAD = "host::\u0000".getBytes("UTF-8");
        } catch (UnsupportedEncodingException var1) {
            ;
        }

    }

    public AdbProtocol() {
    }

    private static int getPayloadChecksum(byte[] payload) {
        int checksum = 0;
        byte[] var5 = payload;
        int var4 = payload.length;

        for(int var3 = 0; var3 < var4; ++var3) {
            byte b = var5[var3];
            if (b >= 0) {
                checksum += b;
            } else {
                checksum += b + 256;
            }
        }

        return checksum;
    }

    public static boolean validateMessage(AdbProtocol.AdbMessage msg) {
        if (msg.command != ~msg.magic) {
            return false;
        } else {
            return msg.payloadLength == 0 || getPayloadChecksum(msg.payload) == msg.checksum;
        }
    }

    public static byte[] generateMessage(int cmd, int arg0, int arg1, byte[] payload) {
        ByteBuffer message;
        if (payload != null) {
            message = ByteBuffer.allocate(24 + payload.length).order(ByteOrder.LITTLE_ENDIAN);//小端模式，高字节在高地址，低字节在低地址
        } else {
            message = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        }

        message.putInt(cmd);
        message.putInt(arg0);
        message.putInt(arg1);
        if (payload != null) {
            message.putInt(payload.length);
            message.putInt(getPayloadChecksum(payload));
        } else {
            message.putInt(0);
            message.putInt(0);
        }

        message.putInt(~cmd);
        if (payload != null) {
            message.put(payload);
        }

        return message.array();
    }

    public static byte[] generateConnect() {
        return generateMessage(1314410051, 16777216, 4096, CONNECT_PAYLOAD);
    }

    public static byte[] generateAuth(int type, byte[] data) {
        return generateMessage(1213486401, type, 0, data);
    }

    public static byte[] generateOpen(int localId, String dest) throws UnsupportedEncodingException {
        ByteBuffer bbuf = ByteBuffer.allocate(dest.length() + 1);
        bbuf.put(dest.getBytes("UTF-8"));
        bbuf.put((byte)0);
        return generateMessage(1313165391, localId, 0, bbuf.array());
    }

    public static byte[] generateWrite(int localId, int remoteId, byte[] data) {
        return generateMessage(1163154007, localId, remoteId, data);
    }

    public static byte[] generateClose(int localId, int remoteId) {
        return generateMessage(1163086915, localId, remoteId, (byte[])null);
    }

    public static byte[] generateReady(int localId, int remoteId) {
        return generateMessage(1497451343, localId, remoteId, (byte[])null);
    }

    static final class AdbMessage {
        public int command;
        public int arg0;
        public int arg1;
        public int payloadLength;
        public int checksum;
        public int magic;
        public byte[] payload;

        AdbMessage() {
        }

        public static AdbProtocol.AdbMessage parseAdbMessage(InputStream in) throws IOException {
            AdbProtocol.AdbMessage msg = new AdbProtocol.AdbMessage();
            ByteBuffer packet = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            int dataRead = 0;

            int bytesRead;
            do {
                bytesRead = in.read(packet.array(), dataRead, 24 - dataRead);
                if (bytesRead < 0) {
                    throw new IOException("Stream closed");
                }

                dataRead += bytesRead;
            } while(dataRead < 24);

            msg.command = packet.getInt();
            msg.arg0 = packet.getInt();
            msg.arg1 = packet.getInt();
            msg.payloadLength = packet.getInt();
            msg.checksum = packet.getInt();
            msg.magic = packet.getInt();
            if (msg.payloadLength != 0) {
                msg.payload = new byte[msg.payloadLength];
                dataRead = 0;

                do {
                    bytesRead = in.read(msg.payload, dataRead, msg.payloadLength - dataRead);
                    if (bytesRead < 0) {
                        throw new IOException("Stream closed");
                    }

                    dataRead += bytesRead;
                } while(dataRead < msg.payloadLength);
            }

            return msg;
        }
    }
}

