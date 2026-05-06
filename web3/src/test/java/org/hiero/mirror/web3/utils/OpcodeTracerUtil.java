// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import java.time.Instant;
import java.util.Comparator;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.service.model.OpcodeRequest;

@UtilityClass
public class OpcodeTracerUtil {

    private static final TransactionIdOrHashParameter DUMMY_TRANSACTION_ID =
            new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH);

    public static final OpcodeContext OPTIONS =
            new OpcodeContext(new OpcodeRequest(DUMMY_TRANSACTION_ID, false, false, false), 0);

    public static String toHumanReadableMessage(final String solidityError) {
        return BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage(solidityError);
    }

    public static Comparator<Long> gasComparator() {
        return (d1, d2) -> {
            final var diff = Math.abs(d1 - d2);
            return Math.toIntExact(diff <= 64L ? 0 : d1 - d2);
        };
    }
}
