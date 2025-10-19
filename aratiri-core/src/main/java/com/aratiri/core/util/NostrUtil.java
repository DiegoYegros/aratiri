package com.aratiri.core.util;

public class NostrUtil {

    public static String npubToHex(String npub) {
        Bech32Util.Bech32Data decoded = Bech32Util.bech32Decode(npub);
        byte[] data = decoded.data();
        byte[] bytes = Bech32Util.convertBits(data, 5, 8, false);
        return Bech32Util.bytesToHex(bytes);
    }

    public static String createSubscriptionRequest(String pubkey, String subscriptionId) {
        return "[\"REQ\", \"" + subscriptionId + "\", {\"authors\": [\"" + pubkey + "\"], \"kinds\": [0], \"limit\": 1}]";
    }
}
