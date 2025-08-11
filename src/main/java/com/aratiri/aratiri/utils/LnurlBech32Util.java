package com.aratiri.aratiri.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LnurlBech32Util {

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GENERATOR = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    public static String encodeLnurl(String url) {
        byte[] data = convertTo5Bit(url.getBytes(StandardCharsets.UTF_8));
        return bech32Encode("lnurl", data);
    }

    private static byte[] convertTo5Bit(byte[] input) {
        int value = 0;
        int bits = 0;
        List<Byte> result = new ArrayList<>();
        for (byte b : input) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.add((byte) ((value >> bits) & 31));
            }
        }
        if (bits > 0) {
            result.add((byte) ((value << (5 - bits)) & 31));
        }
        byte[] out = new byte[result.size()];
        for (int i = 0; i < out.length; i++) out[i] = result.get(i);
        return out;
    }

    private static String bech32Encode(String hrp, byte[] data) {
        byte[] checksum = createChecksum(hrp, data);
        byte[] combined = new byte[data.length + checksum.length];
        System.arraycopy(data, 0, combined, 0, data.length);
        System.arraycopy(checksum, 0, combined, data.length, checksum.length);

        StringBuilder sb = new StringBuilder(hrp.length() + 1 + combined.length);
        sb.append(hrp);
        sb.append('1');
        for (byte b : combined) {
            sb.append(CHARSET.charAt(b));
        }
        return sb.toString();
    }

    private static byte[] createChecksum(String hrp, byte[] data) {
        byte[] values = new byte[hrpExpand(hrp).length + data.length + 6];
        System.arraycopy(hrpExpand(hrp), 0, values, 0, hrpExpand(hrp).length);
        System.arraycopy(data, 0, values, hrpExpand(hrp).length, data.length);
        int polymod = polymod(values) ^ 1;
        byte[] checksum = new byte[6];
        for (int i = 0; i < 6; ++i) {
            checksum[i] = (byte) ((polymod >> 5 * (5 - i)) & 31);
        }
        return checksum;
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
}
