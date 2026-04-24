package com.aratiri.invoices.infrastructure;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class InvoiceUtils {

    private InvoiceUtils() {
    }

    public static byte[] generatePreimage() {
        byte[] preimage = new byte[32];
        new SecureRandom().nextBytes(preimage);
        return preimage;
    }

    public static byte[] sha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }
}
