package com.peerapp;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.util.Base64;

public class EncryptionUtil {
    //Generate an AES secret key to share
    public static SecretKey generateSecretKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(128);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // Encrypt AES key with RSA public key
    public static byte[] encryptAESKey(SecretKey aesKey, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(aesKey.getEncoded());
    }

    // Decrypt AES key with RSA private key
    public static SecretKey decryptAESKey(byte[] encryptedAESKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decodedKey = cipher.doFinal(encryptedAESKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    //Encrypt message with shared secret key
    public static String encrypt(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedData = cipher.doFinal(data.getBytes());

        return Base64.getEncoder().encodeToString(encryptedData);
    }

    //Decrypt message with shared secret key
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);

        return new String(cipher.doFinal(decodedData));
    }

    //Sign message with sender private key (Integrity)
    public static String signMessage(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes());
        byte[] signedMessage = signature.sign();
        return Base64.getEncoder().encodeToString(signedMessage);
    }

    //Verify signature with sender public key
    public static boolean verifySignature(String message, String signedMessage, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(message.getBytes());
        byte[] signatureBytes = Base64.getDecoder().decode(signedMessage);
        return signature.verify(signatureBytes);
    }

    //Generate MAC for message (Integrity) AINDA N USOU
    // public static String generateHMAC(String data, SecretKey key) throws Exception {
    //     byte[] dataBytes = data.getBytes();
    //     Mac hmac = Mac.getInstance("HmacSHA256");
    //     hmac.init(key);
    //     hmac.update(dataBytes);
    //     byte[] hmacData = hmac.doFinal(dataBytes);

    //     return Base64.getEncoder().encodeToString(hmacData);
    // }

    //Verify message MAC AINDA N USOU
    // public static boolean verifyHMAC(String data, String hmac, SecretKey key) throws Exception {
    //     return hmac.equals(generateHMAC(data, key));
    // }

    //Get public key from keystore
    public static PublicKey getPublicKeyFromTrustStore(String trustStorePath, String trustStorePassword, String alias) throws Exception {
        // Load the truststore
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream trustStoreStream = new FileInputStream(trustStorePath)) {
            trustStore.load(trustStoreStream, trustStorePassword.toCharArray());
        }

        //Get the certificate using the alias
        X509Certificate certificate = (X509Certificate) trustStore.getCertificate(alias);
        
        if (certificate == null) {
            throw new Exception("Certificate not found for alias: " + alias);
        }

        //Extract the public key from the certificate
        PublicKey publicKey = certificate.getPublicKey();
        return publicKey;
    }

    //Get private key from keystore
    public static PrivateKey getPrivateKeyFromKeystore(String keystorePath, String keystorePassword, String alias, String keyPassword) throws Exception {
        // Load the keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream keystoreStream = new FileInputStream(keystorePath)) {
            keyStore.load(keystoreStream, keystorePassword.toCharArray());
        }

        // Retrieve the private key using the alias and key password
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword.toCharArray());
        if (privateKey == null) {
            throw new Exception("Private key not found for alias: " + alias);
        }

        return privateKey;
    }

    public static String hashPassword(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 1000, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        byte[] hash = factory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }

    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public static boolean verifyPassword(String inputPassword, String storedHash, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(inputPassword.toCharArray(), salt, 1000, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        byte[] hash = factory.generateSecret(spec).getEncoded();
        String newHash = Base64.getEncoder().encodeToString(hash);

        return newHash.equals(storedHash);
    }
}
