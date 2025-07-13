package com.aratiri.aratiri.config;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

public class MacaroonCallCredentials extends CallCredentials {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String macaroon;

    public MacaroonCallCredentials(String macaroon) {
        this.macaroon = loadMacaroonHex(macaroon);
    }

    private String loadMacaroonHex(String macaroonPath) {
        try {
            byte[] macaroonBytes = Files.readAllBytes(Paths.get(macaroonPath));
            String macaroonHex = new String(macaroonBytes, StandardCharsets.US_ASCII).trim();
            logger.info("the macaroon hex is: {}", macaroonHex);
            return macaroonHex;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load macaroon file from path: " + macaroonPath, e);
        }
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        Metadata headers = new Metadata();
        Metadata.Key<String> macaroonKey = Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(macaroonKey, macaroon);
        logger.info("Applied the headers. Current headers is : {}", headers);
        applier.apply(headers);
    }
}