package com.example.pikamouse.learn_adb.adb.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * @author: jiangfeng
 * @date: 2019/1/8
 */
public class AdbCrypto {
    private KeyPair keyPair;
    private AdbBase64 base64;
    public static final int KEY_LENGTH_BITS = 2048;
    public static final int KEY_LENGTH_BYTES = 256;
    public static final int KEY_LENGTH_WORDS = 64;
    public static final int[] SIGNATURE_PADDING_AS_INT = new int[]{0, 1, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 48, 33, 48, 9, 6, 5, 43, 14, 3, 2, 26, 5, 0, 4, 20};
    public static byte[] SIGNATURE_PADDING;

    static {
        SIGNATURE_PADDING = new byte[SIGNATURE_PADDING_AS_INT.length];

        for(int i = 0; i < SIGNATURE_PADDING.length; ++i) {
            SIGNATURE_PADDING[i] = (byte)SIGNATURE_PADDING_AS_INT[i];
        }

    }

    public AdbCrypto() {
    }

    private static byte[] convertRsaPublicKeyToAdbFormat(RSAPublicKey pubkey) {
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n = pubkey.getModulus();
        BigInteger r = BigInteger.ZERO.setBit(2048);
        BigInteger rr = r.modPow(BigInteger.valueOf(2L), n);
        BigInteger rem = n.remainder(r32);
        BigInteger n0inv = rem.modInverse(r32);
        int[] myN = new int[64];
        int[] myRr = new int[64];

        for(int i = 0; i < 64; ++i) {
            BigInteger[] res = rr.divideAndRemainder(r32);
            rr = res[0];
            rem = res[1];
            myRr[i] = rem.intValue();
            res = n.divideAndRemainder(r32);
            n = res[0];
            rem = res[1];
            myN[i] = rem.intValue();
        }

        ByteBuffer bbuf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        bbuf.putInt(64);
        bbuf.putInt(n0inv.negate().intValue());
        int[] var14 = myN;
        int var13 = myN.length;

        int i;
        int var12;
        for(var12 = 0; var12 < var13; ++var12) {
            i = var14[var12];
            bbuf.putInt(i);
        }

        var14 = myRr;
        var13 = myRr.length;

        for(var12 = 0; var12 < var13; ++var12) {
            i = var14[var12];
            bbuf.putInt(i);
        }

        bbuf.putInt(pubkey.getPublicExponent().intValue());
        return bbuf.array();
    }

    public static AdbCrypto loadAdbKeyPair(AdbBase64 base64, File privateKey, File publicKey) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        AdbCrypto crypto = new AdbCrypto();
        int privKeyLength = (int)privateKey.length();
        int pubKeyLength = (int)publicKey.length();
        byte[] privKeyBytes = new byte[privKeyLength];
        byte[] pubKeyBytes = new byte[pubKeyLength];
        FileInputStream privIn = new FileInputStream(privateKey);
        FileInputStream pubIn = new FileInputStream(publicKey);
        privIn.read(privKeyBytes);
        pubIn.read(pubKeyBytes);
        privIn.close();
        pubIn.close();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pubKeyBytes);
        crypto.keyPair = new KeyPair(keyFactory.generatePublic(publicKeySpec), keyFactory.generatePrivate(privateKeySpec));
        crypto.base64 = base64;
        return crypto;
    }

    public static AdbCrypto generateAdbKeyPair(AdbBase64 base64) throws NoSuchAlgorithmException {
        AdbCrypto crypto = new AdbCrypto();
        KeyPairGenerator rsaKeyPg = KeyPairGenerator.getInstance("RSA");
        rsaKeyPg.initialize(2048);
        crypto.keyPair = rsaKeyPg.genKeyPair();
        crypto.base64 = base64;
        return crypto;
    }

    public byte[] signAdbTokenPayload(byte[] payload) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(1, this.keyPair.getPrivate());
        c.update(SIGNATURE_PADDING);
        return c.doFinal(payload);
    }

    public byte[] getAdbPublicKeyPayload() throws IOException {
        byte[] convertedKey = convertRsaPublicKeyToAdbFormat((RSAPublicKey)this.keyPair.getPublic());
        StringBuilder keyString = new StringBuilder(720);
        keyString.append(this.base64.encodeToString(convertedKey));
        keyString.append(" unknown@unknown");
        keyString.append('\u0000');
        return keyString.toString().getBytes("UTF-8");
    }

    public void saveAdbKeyPair(File privateKey, File publicKey) throws IOException {
        FileOutputStream privOut = new FileOutputStream(privateKey);
        FileOutputStream pubOut = new FileOutputStream(publicKey);
        privOut.write(this.keyPair.getPrivate().getEncoded());
        pubOut.write(this.keyPair.getPublic().getEncoded());
        privOut.close();
        pubOut.close();
    }
}

