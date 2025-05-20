// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.addressFromBytes;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetApprovedResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of GetApprovedPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}
 *  5. Run method only reads from Store and returns {@link RunResult} which is needed in getSuccessResultFor
 */
public class GetApprovedPrecompile extends AbstractReadOnlyPrecompile {

    private static final ABIType<Tuple> HAPI_GET_APPROVED_FUNCTION_DECODER =
            TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_GET_APPROVED_FUNCTION =
            new Function("getApproved(address,uint256)", "(int,int)");
    private static final Bytes HAPI_GET_APPROVED_FUNCTION_SELECTOR = Bytes.wrap(HAPI_GET_APPROVED_FUNCTION.selector());

    public GetApprovedPrecompile(
            SyntheticTxnFactory syntheticTxnFactory, EncodingFacade encoder, PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var inputData = frame.getInputData();
        final var wrapper = decodeGetApproved(inputData);
        validateTrueOrRevert(
                updater.tokenAccessor().isTokenAddress(addressFromBytes(wrapper.token())),
                INVALID_TOKEN_NFT_SERIAL_NUMBER);
        final var spender =
                updater.tokenAccessor().staticApprovedSpenderOf(addressFromBytes(wrapper.token()), wrapper.serialNo());
        return new GetApprovedResult(spender);
    }

    @Override
    public Bytes getSuccessResultFor(RunResult runResult) {
        final var getApprovedResult = (GetApprovedResult) runResult;
        return encoder.encodeGetApproved(SUCCESS.getNumber(), getApprovedResult.spender());
    }

    public static GetApprovedWrapper<byte[]> decodeGetApproved(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, HAPI_GET_APPROVED_FUNCTION_SELECTOR, HAPI_GET_APPROVED_FUNCTION_DECODER);
        final var serialNo = (BigInteger) decodedArguments.get(1);
        return new GetApprovedWrapper<>(decodedArguments.get(0), serialNo.longValueExact());
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_GET_APPROVED);
    }
}
