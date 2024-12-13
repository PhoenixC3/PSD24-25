package com.peerapp;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

public class SSEUtil {

    private static final String HMAC_ALG = "HmacSHA1";
    private static final String CIPHER_ALG = "AES/CBC/PKCS5Padding";
    private static final SecureRandom RND_GENERATOR = new SecureRandom();
    private static IvParameterSpec iv; // Fixed IV for simplicity

    static {
        try {
            byte[] ivBytes = new byte[16];
            RND_GENERATOR.nextBytes(ivBytes);
            iv = new IvParameterSpec(ivBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class ByteArray {

        private byte[] arr;

        public ByteArray(byte[] array) {
            arr = array;
        }

        public byte[] getArr() {
            return arr;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ByteArray && Arrays.equals(arr, ((ByteArray) obj).getArr());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(arr);
        }
    }

    public static class Client {

        private Map<String, Integer> counters;
        private SecretKeySpec sk;
        private Server server;

        public Client(Server server) throws NoSuchAlgorithmException {
            byte[] skBytes = new byte[20];
            RND_GENERATOR.nextBytes(skBytes);
            sk = new SecretKeySpec(skBytes, HMAC_ALG);
            counters = new HashMap<>();
            this.server = server;
        }

        public void update(String keyword, String messageId) throws Exception {
            Mac hmac = Mac.getInstance(HMAC_ALG);
            Cipher aes = Cipher.getInstance(CIPHER_ALG);

            // Generate keys k1 and k2 from keyword and sk using a PRF
            hmac.init(sk);
            byte[] k1 = hmac.doFinal((keyword + "1").getBytes("UTF-8"));
            byte[] k2 = hmac.doFinal((keyword + "2").getBytes("UTF-8"));

            // Get the counter c for keyword from counters, or set it at 0 if not found
            int counter = counters.getOrDefault(keyword, 0);

            // Calculate the index label l through a PRF using k1 as key and c as plaintext
            hmac.init(new SecretKeySpec(k1, HMAC_ALG));
            ByteArray l = new ByteArray(hmac.doFinal(ByteBuffer.allocate(4).putInt(counter).array()));

            // Calculate the index value d through symmetric-key encryption using k2 and messageId
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOf(k2, 16), "AES"), iv);
            ByteArray d = new ByteArray(aes.doFinal(messageId.getBytes("UTF-8")));

            // Send l and d to the server to update the index
            server.update(l, d);

            // Increment counter c and update it in counters
            counters.put(keyword, counter + 1);
        }

        public List<String> search(String keyword) throws Exception {
            Mac hmac = Mac.getInstance(HMAC_ALG);
            Cipher aes = Cipher.getInstance(CIPHER_ALG);

            // Generate k1 and k2 from keyword, as in the update function
            hmac.init(sk);
            byte[] k1 = hmac.doFinal((keyword + "1").getBytes("UTF-8"));
            byte[] k2 = hmac.doFinal((keyword + "2").getBytes("UTF-8"));

            // Send k1 and k2 to the server for search
            return server.search(k1, k2, hmac, aes);
        }
    }

    public static class Server {

        private Map<ByteArray, ByteArray> index;

        public Server() {
            index = new HashMap<>();
        }

        public void update(ByteArray label, ByteArray value) {
            // Update the index with a new entry <label, value>
            index.put(label, value);
        }

        public List<String> search(byte[] k1, byte[] k2, Mac hmac, Cipher aes) throws Exception {
            List<String> results = new ArrayList<>();
            int counter = 0;

            while (true) {
                // Calculate index label with counter and k1 using a PRF
                hmac.init(new SecretKeySpec(k1, HMAC_ALG));
                ByteArray l = new ByteArray(hmac.doFinal(ByteBuffer.allocate(4).putInt(counter).array()));

                // Access the index with the label
                ByteArray value = index.get(l);

                // If entry not found, stop
                if (value == null) {
                    break;
                }

                // If entry found, decrypt it with k2 through symmetric-key decryption
                aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Arrays.copyOf(k2, 16), "AES"), iv);
                String messageId = new String(aes.doFinal(value.getArr()), "UTF-8");

                // Add decrypted messageId to list of results and increment counter
                results.add(messageId);
                counter++;
            }
            return results;
        }
    }
}