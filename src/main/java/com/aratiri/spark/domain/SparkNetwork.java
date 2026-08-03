package com.aratiri.spark.domain;

import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Spark ledger network. The SSP host is shared between REGTEST and MAINNET
 * (api.lightspark.com); the network is what the SDK uses to pick the correct
 * SSP identity key and account-number default (REGTEST=0, MAINNET=1).
 */
public enum SparkNetwork {
    MAINNET,
    REGTEST;

    public static SparkNetwork parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException("network is required", HttpStatus.BAD_REQUEST.value());
        }
        try {
            return SparkNetwork.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(
                    "network must be one of MAINNET, REGTEST",
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }
}
