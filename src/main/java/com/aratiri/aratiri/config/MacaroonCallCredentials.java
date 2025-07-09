package com.aratiri.aratiri.config;

import com.google.common.io.BaseEncoding;
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

import java.util.concurrent.Executor;

public class MacaroonCallCredentials extends CallCredentials {
    private final String macaroon;

    public MacaroonCallCredentials(String macaroon) {
        this.macaroon = loadMacaroonHex(macaroon);
    }

    private String loadMacaroonHex(String macaroonPath) {
        try {
            byte[] macaroonBytes = Files.readAllBytes(Paths.get(macaroonPath));
            return BaseEncoding.base16().lowerCase().encode(macaroonBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load macaroon file from path: " + macaroonPath, e);
        }
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        Metadata headers = new Metadata();
        Metadata.Key<String> macaroonKey = Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(macaroonKey, macaroon);
        applier.apply(headers);
    }
}