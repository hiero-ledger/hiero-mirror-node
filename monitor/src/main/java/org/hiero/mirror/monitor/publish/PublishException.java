// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import com.google.common.base.Throwables;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import io.grpc.StatusRuntimeException;
import lombok.Getter;

@Getter
public class PublishException extends RuntimeException {

    private static final long serialVersionUID = 5825147561227266065L;
    private final transient PublishRequest publishRequest;

    public PublishException(PublishRequest publishRequest, Throwable throwable) {
        super(throwable);
        this.publishRequest = publishRequest;
    }

    public String getStatus() {
        Throwable throwable = Throwables.getRootCause(this);

        return switch (throwable) {
            case PrecheckStatusException pse -> pse.status.toString();
            case ReceiptStatusException rse -> rse.receipt.status.toString();
            case StatusRuntimeException sre -> sre.getStatus().getCode().toString();
            default -> throwable.getClass().getSimpleName();
        };
    }
}
