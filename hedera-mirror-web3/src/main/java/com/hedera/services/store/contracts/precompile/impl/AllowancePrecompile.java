// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.addressFromBytes;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_TRIO_RAW_TYPE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalseOrRevert;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.AllowanceResult;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of AllowancePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}
 *  5. Run method only reads from Store and returns {@link RunResult} which is needed in getSuccessResultFor
 */
public class AllowancePrecompile extends AbstractReadOnlyPrecompile {

    private static final Function HAPI_ALLOWANCE_FUNCTION =
            new Function("allowance(address,address,address)", "(int,int)");
    private static final Bytes HAPI_ALLOWANCE_SELECTOR = Bytes.wrap(HAPI_ALLOWANCE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_ALLOWANCE_DECODER = TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);

    public AllowancePrecompile(
            SyntheticTxnFactory syntheticTxnFactory, EncodingFacade encoder, PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var inputData = frame.getInputData();
        final var wrapper = decodeTokenAllowance(inputData);
        final var store = updater.getStore();
        final var account = store.getAccount(addressFromBytes(wrapper.owner()), OnMissing.THROW);
        validateFalseOrRevert(account.isEmptyAccount(), INVALID_ACCOUNT_ID);

        final var allowances = updater.tokenAccessor()
                .staticAllowanceOf(
                        addressFromBytes(wrapper.owner()),
                        addressFromBytes(wrapper.spender()),
                        addressFromBytes(wrapper.token()));
        return new AllowanceResult(allowances);
    }

    @Override
    public Bytes getSuccessResultFor(RunResult runResult) {
        final var allowanceResult = (AllowanceResult) runResult;
        return encoder.encodeAllowance(SUCCESS.getNumber(), allowanceResult.allowances());
    }

    public static TokenAllowanceWrapper<byte[], byte[], byte[]> decodeTokenAllowance(final Bytes input) {
        final Tuple decodedArguments = decodeFunctionCall(input, HAPI_ALLOWANCE_SELECTOR, HAPI_ALLOWANCE_DECODER);
        return new TokenAllowanceWrapper<>(decodedArguments.get(0), decodedArguments.get(1), decodedArguments.get(2));
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_ALLOWANCE);
    }
}
