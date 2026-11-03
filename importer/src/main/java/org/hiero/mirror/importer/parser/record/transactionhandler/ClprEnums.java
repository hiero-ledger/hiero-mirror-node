// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.importer.util.Utility;

@UtilityClass
class ClprEnums {

    static ClprMessageReplyStatus toReplyStatus(com.hederahashgraph.api.proto.java.ClprMessageReplyStatus status) {
        return switch (status) {
            case SUCCESS -> ClprMessageReplyStatus.SUCCESS;
            case APPLICATION_ERROR -> ClprMessageReplyStatus.APPLICATION_ERROR;
            case CONNECTOR_NOT_FOUND -> ClprMessageReplyStatus.CONNECTOR_NOT_FOUND;
            case CONNECTOR_UNDERFUNDED -> ClprMessageReplyStatus.CONNECTOR_UNDERFUNDED;
            default -> {
                // REDACTED and CHANNEL_CLOSED are reserved and never generated
                Utility.handleRecoverableError("Unexpected CLPR message reply status {}", status);
                yield null;
            }
        };
    }

    static ClprSignatureScheme toSignatureScheme(com.hederahashgraph.api.proto.java.ClprSignatureScheme scheme) {
        return switch (scheme) {
            case ECDSA_SECP256K1 -> ClprSignatureScheme.ECDSA_SECP256K1;
            case ED25519 -> ClprSignatureScheme.ED25519;
            default -> {
                Utility.handleRecoverableError("Unexpected CLPR signature scheme {}", scheme);
                yield null;
            }
        };
    }
}
