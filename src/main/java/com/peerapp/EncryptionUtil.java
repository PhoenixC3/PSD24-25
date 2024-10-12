package com.peerapp;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;

public class EncryptionUtil {
    private static final String AES = "AES";
    private static final String HMAC_SHA256 = "HmacSHA256";

    public static String encrypt(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(data.getBytes());
        return java.util.Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        byte[] decoded = java.util.Base64.getDecoder().decode(encryptedData);
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted);
    }

    public static String generateHMAC(String data, SecretKey key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(key);
        byte[] hmac = mac.doFinal(data.getBytes());
        return java.util.Base64.getEncoder().encodeToString(hmac);
    }

    public static boolean verifyHMAC(String data, String hmacToVerify, SecretKey key) throws Exception {
        String hmac = generateHMAC(data, key);
        return hmac.equals(hmacToVerify);
    }
}
