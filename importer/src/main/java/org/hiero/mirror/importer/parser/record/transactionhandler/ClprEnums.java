// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.clpr.ClprMaintenanceReason;
import org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@UtilityClass
final class ClprEnums {

    static @Nullable ClprMaintenanceReason toMaintenanceReason(
            com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason reason) {
        try {
            return ClprMaintenanceReason.valueOf(reason.name());
        } catch (final IllegalArgumentException e) {
            // UNRECOGNIZED only, for a reason value newer than this mirror node knows about
            Utility.handleRecoverableError("Unexpected CLPR maintenance reason {}", reason);
            return null;
        }
    }

    static @Nullable ClprMessageReplyStatus toReplyStatus(
            com.hederahashgraph.api.proto.java.ClprMessageReplyStatus status) {
        try {
            return ClprMessageReplyStatus.valueOf(status.name());
        } catch (final IllegalArgumentException e) {
            // REDACTED and CHANNEL_CLOSED are reserved and never generated
            Utility.handleRecoverableError("Unexpected CLPR message reply status {}", status);
            return null;
        }
    }

    static @Nullable ClprSignatureScheme toSignatureScheme(
            com.hederahashgraph.api.proto.java.ClprSignatureScheme scheme) {
        try {
            return ClprSignatureScheme.valueOf(scheme.name());
        } catch (final IllegalArgumentException e) {
            Utility.handleRecoverableError("Unexpected CLPR signature scheme {}", scheme);
            return null;
        }
    }
}
