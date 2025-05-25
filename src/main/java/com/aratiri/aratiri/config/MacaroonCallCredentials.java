package com.aratiri.aratiri.config;

import io.grpc.CallCredentials;
import io.grpc.Metadata;

import java.util.concurrent.Executor;

public class MacaroonCallCredentials extends CallCredentials {
    private final String macaroon;

    public MacaroonCallCredentials(String macaroon) {
        this.macaroon = macaroon;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        Metadata headers = new Metadata();
        Metadata.Key<String> macaroonKey = Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(macaroonKey, macaroon);
        applier.apply(headers);
    }
}