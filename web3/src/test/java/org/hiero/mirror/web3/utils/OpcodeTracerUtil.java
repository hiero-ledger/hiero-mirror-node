// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import java.util.Comparator;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;

@UtilityClass
public class OpcodeTracerUtil {

    public static final OpcodeTracerOptions OPTIONS = new OpcodeTracerOptions(false, false, false);

    public static String toHumanReadableMessage(final String solidityError) {
        return BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage(Bytes.fromHexString(solidityError));
    }

    public static Comparator<Long> gasComparator() {
        return (d1, d2) -> {
            final var diff = Math.abs(d1 - d2);
            return Math.toIntExact(diff <= 64L ? 0 : d1 - d2);
        };
    }
}
