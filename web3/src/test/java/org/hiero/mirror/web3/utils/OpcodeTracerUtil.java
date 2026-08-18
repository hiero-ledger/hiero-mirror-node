// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import java.time.Instant;
import java.util.Comparator;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.service.model.OpcodeRequest;

@UtilityClass
public class OpcodeTracerUtil {

    private static final TransactionIdOrHashParameter DUMMY_TRANSACTION_ID =
            new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH);

    private static final OpcodesProperties OPCODES_PROPERTIES = new OpcodesProperties();

    /**
     * Builds a fresh {@link OpcodeContext} for a single opcode-trace call. Production creates a new context per request
     * (see OpcodeServiceImpl#processOpcodeCall); tests must do the same because the context accumulates mutable state
     * (opcodes, capture budgets, per-depth counters) as opcodes are recorded. Sharing a single static instance across
     * tests lets earlier tests fill the trace to its cap, which corrupts assertions in later tests.
     */
    public static OpcodeContext options() {
        return new OpcodeContext(new OpcodeRequest(DUMMY_TRANSACTION_ID, false, false, false), 0, OPCODES_PROPERTIES);
    }

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
