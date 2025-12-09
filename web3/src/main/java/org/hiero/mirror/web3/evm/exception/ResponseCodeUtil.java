// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.exception;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.store.contracts.utils.BytesKey;
import java.util.Map;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

@UtilityClass
public class ResponseCodeUtil {
    static final Map<BytesKey, ResponseCodeEnum> RESOURCE_EXHAUSTION_REVERSIONS = Stream.of(
                    MAX_CHILD_RECORDS_EXCEEDED,
                    MAX_CONTRACT_STORAGE_EXCEEDED,
                    MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED,
                    MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED,
                    INSUFFICIENT_BALANCES_FOR_STORAGE_RENT,
                    INVALID_TRANSACTION,
                    CONTRACT_EXECUTION_EXCEPTION,
                    INVALID_SOLIDITY_ADDRESS,
                    INVALID_CONTRACT_ID,
                    CONTRACT_NEGATIVE_VALUE,
                    INSUFFICIENT_PAYER_BALANCE)
            .collect(toMap(
                    status ->
                            new BytesKey(new MirrorEvmTransactionException(status, StringUtils.EMPTY, StringUtils.EMPTY)
                                    .messageBytes()
                                    .toArrayUnsafe()),
                    status -> status));

    public static ResponseCodeEnum getStatusOrDefault(final HederaEvmTransactionProcessingResult result) {
        if (result.isSuccessful()) {
            return SUCCESS;
        }
        var maybeHaltReason = result.getHaltReason();
        if (maybeHaltReason.isPresent()) {
            var haltReason = maybeHaltReason.get();
            if (ExceptionalHaltReason.ILLEGAL_STATE_CHANGE == haltReason) {
                return ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
            } else if (ExceptionalHaltReason.INSUFFICIENT_GAS == haltReason) {
                return ResponseCodeEnum.INSUFFICIENT_GAS;
            } else if (HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS == haltReason) {
                return INVALID_SOLIDITY_ADDRESS;
            }
        }

        return result.getRevertReason()
                .map(status -> RESOURCE_EXHAUSTION_REVERSIONS.getOrDefault(
                        new BytesKey(result.getRevertReason().get().toArrayUnsafe()), CONTRACT_REVERT_EXECUTED))
                .orElse(CONTRACT_EXECUTION_EXCEPTION);
    }
}
