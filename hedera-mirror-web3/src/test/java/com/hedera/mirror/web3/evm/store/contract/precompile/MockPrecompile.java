// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.contract.precompile;

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

@Named
public class MockPrecompile implements Precompile {

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        return TransactionBody.newBuilder();
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        return 0;
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        // Dummy logic to mimic invalid behaviour
        if (Address.ZERO.equals(frame.getSenderAddress())) {
            throw new InvalidTransactionException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        } else if (Address.ECREC.equals(frame.getSenderAddress())) {
            return null;
        } else {
            return new EmptyRunResult();
        }
    }

    @Override
    public long getGasRequirement(
            final long blockTimestamp, final TransactionBody.Builder transactionBody, final AccountID sender) {
        return 0;
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(0x00000000);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        if (runResult == null) {
            return null;
        } else {
            return SUCCESS_RESULT;
        }
    }
}
