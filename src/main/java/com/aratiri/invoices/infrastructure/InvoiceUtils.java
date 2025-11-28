package com.aratiri.invoices.infrastructure;

import com.aratiri.shared.util.Bech32Util;

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

    static void main(String[] args) throws NoSuchAlgorithmException {
        byte[] preimage = generatePreimage();
        byte[] paymentHash = sha256(preimage);

        System.out.println("Preimage: " + Bech32Util.bytesToHex(preimage));
        System.out.println("Payment Hash: " + Bech32Util.bytesToHex(paymentHash));
    }

}
