package com.peerapp;

import java.io.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShamirUtil {

    private static final SecureRandom random = new SecureRandom();
    private static final BigInteger PRIME = BigInteger.probablePrime(2048, random); // Larger prime for RSA compatibility

    public static List<Share> splitSecret(byte[] secret, int n, int k) {
        EncapsulatedSecret encapsulatedSecret = new EncapsulatedSecret(secret);
        byte[] secretBytes = serialize(encapsulatedSecret);
    
        BigInteger secretInt = new BigInteger(1, secretBytes);
        List<BigInteger> coefficients = new ArrayList<>();
        coefficients.add(secretInt);
    
        for (int i = 1; i < k; i++) {
            coefficients.add(new BigInteger(PRIME.bitLength(), random).mod(PRIME));
        }
    
        List<Share> shares = new ArrayList<>();
        for (int x = 1; x <= n; x++) {
            BigInteger y = BigInteger.ZERO;
            for (int i = 0; i < k; i++) {
                y = y.add(coefficients.get(i).multiply(BigInteger.valueOf(x).pow(i))).mod(PRIME);
            }
            shares.add(new Share(x, y));
        }
    
        return shares;
    }

    private static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
                out.flush();
                out.writeObject(obj);
                out.flush();
                out.reset();
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Serialization failed", e);
        }
    }

    public static byte[] reconstructSecret(List<Share> shares) {
        BigInteger secret = BigInteger.ZERO;
    
        for (Share share : shares) {
            if (!share.isValid()) {
                throw new IllegalArgumentException("Invalid share detected");
            }
        }
    
        for (int i = 0; i < shares.size(); i++) {
            BigInteger xi = BigInteger.valueOf(shares.get(i).getX());
            BigInteger yi = shares.get(i).getY();
            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;
    
            for (int j = 0; j < shares.size(); j++) {
                if (i != j) {
                    BigInteger xj = BigInteger.valueOf(shares.get(j).getX());
                    numerator = numerator.multiply(xj.negate()).mod(PRIME);
                    denominator = denominator.multiply(xi.subtract(xj)).mod(PRIME);
                }
            }
    
            BigInteger lagrange = numerator.multiply(denominator.modInverse(PRIME)).mod(PRIME);
            secret = secret.add(yi.multiply(lagrange)).mod(PRIME);
        }
    
        byte[] secretBytes = secret.toByteArray();
        if (secretBytes[0] == 0) {
            byte[] adjusted = Arrays.copyOfRange(secretBytes, 1, secretBytes.length);
            secretBytes = adjusted;
        }
    
        int expectedLength = EncapsulatedSecret.class.getDeclaredFields().length;
        if (secretBytes.length < expectedLength) {
            byte[] paddedBytes = new byte[expectedLength];
            System.arraycopy(secretBytes, 0, paddedBytes, paddedBytes.length - secretBytes.length, secretBytes.length);
            secretBytes = paddedBytes;
        } else if (secretBytes.length > expectedLength) {
            secretBytes = Arrays.copyOfRange(secretBytes, secretBytes.length - expectedLength, secretBytes.length);
        }
    
        EncapsulatedSecret encapsulatedSecret = deserialize(secretBytes, EncapsulatedSecret.class);
        return encapsulatedSecret.getData();
    }

    private static <T> T deserialize(byte[] data, Class<T> clazz) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return clazz.cast(in.readObject());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    // Serializable class to encapsulate secret data with its length
    public static class EncapsulatedSecret implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int length;
        private final byte[] data;

        public EncapsulatedSecret(byte[] data) {
            this.length = data.length;
            this.data = data;
        }

        public int getLength() {
            return length;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static class Share implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int x;
        private final BigInteger y;
        private final int checksum;
    
        public Share(int x, BigInteger y) {
            this.x = x;
            this.y = y;
            this.checksum = calculateChecksum();
        }
    
        public int getX() {
            return x;
        }
    
        public BigInteger getY() {
            return y;
        }
    
        public int getChecksum() {
            return checksum;
        }
    
        private int calculateChecksum() {
            return x ^ y.hashCode();
        }
    
        public boolean isValid() {
            return calculateChecksum() == checksum;
        }
    }
}