package com.aratiri.infrastructure.grpc;

import com.aratiri.infrastructure.filter.LogUtils;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcLoggingInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcLoggingInterceptor.class);

    @Override
    public <Q, S> ClientCall<Q, S> interceptCall(
            MethodDescriptor<Q, S> method,
            CallOptions callOptions,
            Channel next) {
        if (log.isInfoEnabled()) {
            log.info(LogUtils.formatKeyValue("METHOD", method.getFullMethodName()));
        }

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<S> responseListener, Metadata headers) {
                if (log.isInfoEnabled()) {
                    log.info(LogUtils.formatKeyValue("METADATA (HEADERS) SENT", ""));
                    headers.keys().forEach(key -> log.info(LogUtils.formatKeyValue("  " + key, headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)))));
                }

                Listener<S> forwardingListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onMessage(S message) {
                        if (log.isDebugEnabled()) {
                            log.debug(LogUtils.formatKeyValue("SERVER'S RESPONSE", "\n" + message));
                        }
                        super.onMessage(message);
                    }

                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (!status.isOk()) {
                            log.debug(LogUtils.formatKeyValue("gRPC CALL CLOSED WITH ERROR", status));
                        } else {
                            log.debug(LogUtils.formatKeyValue("gRPC CALL STATUS", "SUCCESSFULLY CLOSED"));
                        }
                        super.onClose(status, trailers);
                    }
                };

                super.start(forwardingListener, headers);
            }

            @Override
            public void sendMessage(Q message) {
                if (log.isInfoEnabled()) {
                    log.info(LogUtils.formatKeyValue("MESSAGE SENT TO SERVER", "\n" + message));
                }
                super.sendMessage(message);
            }
        };
    }
}
