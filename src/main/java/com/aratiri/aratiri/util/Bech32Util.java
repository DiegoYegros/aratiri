// src/main/java/com/aratiri/aratiri/util/Bech32Util.java
package com.aratiri.aratiri.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A utility class for Bech32 encoding and decoding, adapted for both LNURL and Nostr npub formats.
 * This implementation is self-contained and does not require external dependencies.
 */
public class Bech32Util {

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GENERATOR = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    /**
     * A simple container for the decoded parts of a Bech32 string.
     */
    public static class Bech32Data {
        public final String hrp;
        public final byte[] data;

        public Bech32Data(String hrp, byte[] data) {
            this.hrp = hrp;
            this.data = data;
        }
    }

    /**
     * Decodes a Bech32 string into its human-readable part and data payload.
     *
     * @param bech32 The Bech32 string to decode.
     * @return A Bech32Data object containing the hrp and data.
     */
    public static Bech32Data bech32Decode(final String bech32) {
        if (!bech32.equals(bech32.toLowerCase(Locale.ROOT)) && !bech32.equals(bech32.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Bech32 cannot mix case");
        }
        String lowerBech32 = bech32.toLowerCase(Locale.ROOT);
        int pos = lowerBech32.lastIndexOf('1');
        if (pos < 1 || pos + 7 > lowerBech32.length() || lowerBech32.length() > 90) {
            throw new IllegalArgumentException("Invalid Bech32 string");
        }
        for (int i = 0; i < pos; ++i) {
            if (lowerBech32.charAt(i) < 33 || lowerBech32.charAt(i) > 126) {
                throw new IllegalArgumentException("Invalid character in HRP");
            }
        }

        String hrp = lowerBech32.substring(0, pos);
        byte[] data = new byte[lowerBech32.length() - pos - 1];
        for (int i = 0, j = pos + 1; j < lowerBech32.length(); i++, j++) {
            int index = CHARSET.indexOf(lowerBech32.charAt(j));
            if (index < 0) {
                throw new IllegalArgumentException("Invalid character in data part");
            }
            data[i] = (byte) index;
        }

        if (!verifyChecksum(hrp, data)) {
            throw new IllegalArgumentException("Invalid checksum");
        }

        return new Bech32Data(hrp, Arrays.copyOfRange(data, 0, data.length - 6));
    }

    /**
     * Converts an array of bytes from one base to another, e.g., 5-bit to 8-bit.
     *
     * @param in       The input byte array.
     * @param fromBits The number of bits per input value.
     * @param toBits   The number of bits per output value.
     * @param pad      Whether to pad the output.
     * @return The converted byte array.
     */
    public static byte[] convertBits(final byte[] in, final int fromBits, final int toBits, final boolean pad) {
        int acc = 0;
        int bits = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        final int maxv = (1 << toBits) - 1;
        final int max_acc = (1 << (fromBits + toBits - 1)) - 1;
        for (final byte value : in) {
            int b = value & 0xff;
            acc = ((acc << fromBits) | b) & max_acc;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                out.write((acc >>> bits) & maxv);
            }
        }
        if (pad) {
            if (bits > 0) {
                out.write((acc << (toBits - bits)) & maxv);
            }
        } else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
            throw new IllegalArgumentException("Invalid padding");
        }
        return out.toByteArray();
    }


    private static boolean verifyChecksum(String hrp, byte[] data) {
        byte[] exp = hrpExpand(hrp);
        byte[] values = new byte[exp.length + data.length];
        System.arraycopy(exp, 0, values, 0, exp.length);
        System.arraycopy(data, 0, values, exp.length, data.length);
        return polymod(values) == 1;
    }

    private static byte[] hrpExpand(String hrp) {
        int length = hrp.length();
        byte[] result = new byte[length * 2 + 1];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (hrp.charAt(i) >> 5);
        }
        result[length] = 0;
        for (int i = 0; i < length; i++) {
            result[length + 1 + i] = (byte) (hrp.charAt(i) & 31);
        }
        return result;
    }

    private static int polymod(byte[] values) {
        int chk = 1;
        for (byte b : values) {
            int top = chk >> 25;
            chk = (chk & 0x1ffffff) << 5 ^ (b & 0xff);
            for (int i = 0; i < 5; ++i) {
                chk ^= ((top >> i) & 1) != 0 ? GENERATOR[i] : 0;
            }
        }
        return chk;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
