// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SequencedCollection;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.exception.EvmException;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;

@Getter
@SuppressWarnings("java:S110")
public class MirrorEvmTransactionException extends EvmException {

    @Serial
    private static final long serialVersionUID = 2244739157125796266L;

    private ResponseCodeEnum responseCode;
    private final String detail;
    private final String data;
    private final transient EvmTransactionResult result;
    // needs to be initialized since some tests fail, opted for List.of() instead of LinkedList because
    // in one of the constructors we reassign it
    private SequencedCollection<String> childTransactionErrors = List.of();

    public MirrorEvmTransactionException(
            final ResponseCodeEnum responseCode, final String detail, final String hexData) {
        this(responseCode.name(), detail, hexData, null);
        this.responseCode = responseCode;
    }

    public MirrorEvmTransactionException(final String detail, final String hexData) {
        this(null, detail, hexData, null);
    }

    public MirrorEvmTransactionException(
            final String message, final String detail, final String hexData, final EvmTransactionResult result) {
        super(message);
        this.detail = detail;
        this.data = hexData;
        this.result = result;
    }

    public MirrorEvmTransactionException(
            final ResponseCodeEnum responseCode,
            final String detail,
            final String hexData,
            final EvmTransactionResult result,
            final SequencedCollection<String> childTransactionErrors) {
        this(responseCode.name(), detail, hexData, result);
        this.childTransactionErrors = childTransactionErrors;
    }

    public Bytes messageBytes() {
        final var message = getMessage();
        return Bytes.of(message.getBytes(StandardCharsets.UTF_8));
    }

    public String getFullMessage() {
        final var exceptionMessageBuilder =
                new StringBuilder().append("Mirror EVM transaction error: ").append(getMessage());
        if (!StringUtils.isBlank(getDetail())) {
            exceptionMessageBuilder.append(", detail: ").append(getDetail());
        }
        if (getChildTransactionErrors() != null && !getChildTransactionErrors().isEmpty()) {
            exceptionMessageBuilder.append(", childTransactionErrors: ").append(getChildTransactionErrors());
        }
        exceptionMessageBuilder.append(", data: ").append(getData());
        return exceptionMessageBuilder.toString();
    }

    @Override
    public String toString() {
        return "%s(message=%s, detail=%s, data=%s, dataDecoded=%s)"
                .formatted(getClass().getSimpleName(), getMessage(), detail, data, decodeHex(data));
    }

    private String decodeHex(final String hex) {
        try {
            if (StringUtils.isBlank(hex)) {
                return EMPTY;
            }

            var decoded = Hex.decodeHex(hex.replace("0x", EMPTY));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return EMPTY;
        }
    }
}
