package com.aratiri.infrastructure.grpc;

import com.aratiri.infrastructure.filter.LogUtils;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
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
                Listener<S> forwardingListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (log.isInfoEnabled()) {
                            log.info(LogUtils.formatKeyValue("gRPC CALL STATUS", status.getCode()));
                        }
                        super.onClose(status, trailers);
                    }
                };

                super.start(forwardingListener, headers);
            }
        };
    }
}
