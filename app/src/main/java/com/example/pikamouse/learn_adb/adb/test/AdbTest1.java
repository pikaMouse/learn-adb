package com.example.pikamouse.learn_adb.adb.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * @author: jiangfeng
 * @date: 2019/1/8
 */
public class AdbTest1 {

    public static void main(String[] args) {
        Socket socket = null;
        try {
            socket = new Socket("127.0.0.1", 5037);
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();
            String dest = "000Chost:version";
            ByteBuffer bbuf = ByteBuffer.allocate(dest.length() + 1);
            bbuf.put(dest.getBytes("UTF-8"));
            outputStream.write(bbuf.array());
            while (true) {
               BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
               String line = bufferedReader.readLine();
               if (line != null) {
                   System.out.print(line);
                   break;
               }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
