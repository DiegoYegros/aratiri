package com.aratiri.aratiri.interceptor;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcLoggingInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcLoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        log.info("==================== START gRPC CALL ====================");
        log.info("METHOD                 : {}", method.getFullMethodName());

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                log.info("METADATA (HEADERS) SENT:");
                headers.keys().forEach(key -> {
                    log.info("  {}: {}", key, headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                });

                Listener<RespT> forwardingListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onMessage(RespT message) {
                        log.info("SERVER'S RESPONSE:\n{}", message.toString());
                        super.onMessage(message);
                    }

                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (!status.isOk()) {
                            log.error("gRPC CALL CLOSED WITH ERROR: {}", status);
                        } else {
                            log.info("gRPC CALL SUCCESSFULLY CLOSED");
                        }
                        log.info("==================== END gRPC CALL ====================");
                        super.onClose(status, trailers);
                    }
                };

                super.start(forwardingListener, headers);
            }

            @Override
            public void sendMessage(ReqT message) {
                log.info("MESSAGE SENT TO SERVER :\n{}", message.toString());
                super.sendMessage(message);
            }
        };
    }
}