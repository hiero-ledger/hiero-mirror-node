// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.util;

import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;

import com.google.common.base.Splitter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.rest.model.ContractCallResponse;

/**
 * Wraps an instance of OpenAPI model object {@link ContractCallResponse} and provides additional convenience
 * functionality that was present in the POJOs used previously.
 */
@RequiredArgsConstructor(staticName = "of")
public class ContractCallResponseWrapper {
    private static final String EMPTY_RESULT = "0x0000000000000000000000000000000000000000000000000000000000000000";

    @NonNull
    private final ContractCallResponse response;

    public String getResult() {
        return response.getResult();
    }

    public BigInteger getResultAsNumber() {
        var result = getResult();
        if (!Strings.CS.startsWith(result, HEX_PREFIX)) {
            return new BigInteger(result);
        } else {
            return getResultAsBytes().toBigInteger();
        }
    }

    public String getResultAsSelector() {
        return getResultAsBytes().trimTrailingZeros().toUnprefixedHexString();
    }

    public String getResultAsAddress() {
        return getResultAsBytes().slice(12).toUnprefixedHexString();
    }

    public boolean getResultAsBoolean() {
        return Long.parseUnsignedLong(response.getResult().replace(HEX_PREFIX, ""), 16) > 0;
    }

    public Bytes getResultAsBytes() {
        var result = getResult();
        if (StringUtils.isEmpty(result) || EMPTY_RESULT.equals(result)) {
            return Bytes.EMPTY;
        }

        if (result.startsWith(HEX_PREFIX)) {
            return Bytes.fromHexString(result);
        } else {
            return Bytes.wrap(result.getBytes());
        }
    }

    public String getResultAsText() {
        var bytes = getResultAsBytes().toArrayUnsafe();
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    public String getResultAsAsciiString() {
        // 1st 32 bytes - string info
        // 2nd 32 bytes - data length in the last 32 bytes
        // 3rd 32 bytes - actual string suffixed with zeroes
        return hexToAscii(
                response.getResult().replace(HEX_PREFIX, "").substring(128).trim());
    }

    public List<BigInteger> getResultAsListDecimal() {
        var result = response.getResult().replace(HEX_PREFIX, "");

        return Splitter.fixedLength(64)
                .splitToStream(result)
                .map(TestUtil::hexToDecimal)
                .toList();
    }

    public List<String> getResultAsListAddress() {
        var result = response.getResult().replace(HEX_PREFIX, "");

        return Splitter.fixedLength(64).splitToStream(result).toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContractCallResponseWrapper contractCallResponseWrapper = (ContractCallResponseWrapper) o;
        return Objects.equals(this.response, contractCallResponseWrapper.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(response);
    }

    @Override
    public String toString() {
        return response.getResult();
    }
}
