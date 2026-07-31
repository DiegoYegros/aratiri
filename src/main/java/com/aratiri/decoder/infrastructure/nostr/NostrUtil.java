package com.aratiri.decoder.infrastructure.nostr;

import com.aratiri.bitcoin.Bech32;

public class NostrUtil {

    private NostrUtil() {
    }

    public static String npubToHex(String npub) {
        Bech32.Data decoded = Bech32.decode(npub);
        byte[] data = decoded.data();
        byte[] bytes = Bech32.convertBits(data, 5, 8, false);
        return Bech32.bytesToHex(bytes);
    }

    public static String createSubscriptionRequest(String pubkey, String subscriptionId) {
        return "[\"REQ\", \"" + subscriptionId + "\", {\"authors\": [\"" + pubkey + "\"], \"kinds\": [0], \"limit\": 1}]";
    }
}
