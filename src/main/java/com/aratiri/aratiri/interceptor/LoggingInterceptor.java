package com.aratiri.aratiri.interceptor;

import io.grpc.*;

public class LoggingInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                for (String key : headers.keys()) {
                    if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                        continue;
                    }
                    System.out.println("Metadata: " + key + " = " + headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                }
                super.start(responseListener, headers);
            }
        };
    }
}