// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.net.SocketAddress;
import org.springframework.grpc.server.GlobalServerInterceptor;

@GlobalServerInterceptor
public class RemoteAddressInterceptor implements ServerInterceptor {

    public static final Context.Key<SocketAddress> REMOTE_ADDRESS = Context.key("grpc-remote-address");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        var context = Context.current();
        final var remoteAddress = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        if (remoteAddress != null) {
            context = context.withValue(REMOTE_ADDRESS, remoteAddress);
        }
        return Contexts.interceptCall(context, call, headers, next);
    }
}
