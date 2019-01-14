package com.example.pikamouse.learn_adb;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * create by jiangfeng 2019/1/12
 */
public class AdbTest2 {

    public static void main(String[] args) {
        try {
            byte[]array = new byte[]{1,0,1,0,0,1};
            ByteBuffer bbf = ByteBuffer.allocate(array.length);
            bbf.put(array);
            int checksum = getPayloadChecksum(bbf.array());
            System.out.print(checksum);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static int getPayloadChecksum(byte[] payload) {
        int checksum = 0;
        byte[] var5 = payload;
        int var4 = payload.length;

        for (int var3 = 0; var3 < var4; ++var3) {
            byte b = var5[var3];
            if (b >= 0) {
                checksum += b;
            } else {
                checksum += b + 256;
            }
        }

        return checksum;

    }

}
