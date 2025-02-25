// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Extracted interface from hedera-services
 *
 * Differences from the original:
 *  1. Added record types for input arguments and return types, so that the Precompile implementation could achieve statless behaviour
 *  2. Added senderAccount to the getMinimumFeeInTinybars and getGasRequirement methods, so that the Precompile implementation could achieve statless behaviour
 */
public interface Precompile {

    // Construct the synthetic transaction
    TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams);

    // Customize fee charging
    long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody, AccountID senderAccount);

    // Change the world state through the given frame
    RunResult run(MessageFrame frame, TransactionBody transactionBody);

    long getGasRequirement(long blockTimestamp, TransactionBody.Builder transactionBody, AccountID senderAccount);

    Set<Integer> getFunctionSelectors();

    default void handleSentHbars(final MessageFrame frame, final TransactionBody.Builder transactionBody) {
        if (!Objects.equals(Wei.ZERO, frame.getValue())) {
            final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
            frame.setRevertReason(Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8)));
            frame.setState(REVERT);
            throw new InvalidTransactionException(INVALID_FEE_SUBMITTED, true);
        }
    }

    default Bytes getSuccessResultFor(final RunResult runResult) {
        return SUCCESS_RESULT;
    }

    default Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return EncodingFacade.resultFrom(status);
    }
}
