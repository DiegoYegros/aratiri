package com.aratiri.aratiri.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class InvoiceUtils {

    public static byte[] generatePreimage() {
        byte[] preimage = new byte[32];
        new SecureRandom().nextBytes(preimage);
        return preimage;
    }

    public static byte[] sha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        byte[] preimage = generatePreimage();
        byte[] paymentHash = sha256(preimage);

        System.out.println("Preimage: " + bytesToHex(preimage));
        System.out.println("Payment Hash: " + bytesToHex(paymentHash));
    }

}
