// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTxProcessor;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Account;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Map;
import javax.inject.Provider;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

@Named
public class MirrorEvmTxProcessorImpl extends HederaEvmTxProcessor implements MirrorEvmTxProcessor {

    private final AbstractCodeCache codeCache;
    private final MirrorEvmContractAliases aliasManager;
    private final Store store;
    private final EntityAddressSequencer entityAddressSequencer;
    private final TokenAccessor tokenAccessor;
    private final CodeFactory codeFactory;

    @SuppressWarnings("java:S107")
    public MirrorEvmTxProcessorImpl(
            final HederaEvmMutableWorldState worldState,
            final PricesAndFeesProvider pricesAndFeesProvider,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<SemanticVersion, Provider<MessageCallProcessor>> mcps,
            final Map<SemanticVersion, Provider<ContractCreationProcessor>> ccps,
            final HederaEvmBlocks blockMetaSource,
            final MirrorEvmContractAliases aliasManager,
            final AbstractCodeCache codeCache,
            final Map<TracerType, Provider<HederaEvmOperationTracer>> tracerMap,
            final Store store,
            final EntityAddressSequencer entityAddressSequencer,
            final TokenAccessor tokenAccessor,
            final CodeFactory codeFactory) {
        super(
                worldState,
                pricesAndFeesProvider,
                dynamicProperties,
                gasCalculator,
                mcps,
                ccps,
                blockMetaSource,
                tracerMap);

        this.aliasManager = aliasManager;
        this.codeCache = codeCache;
        this.store = store;
        this.entityAddressSequencer = entityAddressSequencer;
        this.tokenAccessor = tokenAccessor;
        this.codeFactory = codeFactory;
    }

    public HederaEvmTransactionProcessingResult execute(final CallServiceParameters params, final long estimatedGas) {
        final long gasPrice = gasPriceTinyBarsGiven(Instant.now());

        store.wrap();
        if (store.getAccount(params.getSender(), OnMissing.DONT_THROW).isEmptyAccount()) {

            final Account senderAccount;
            if (isMirror(params.getSender())) {
                senderAccount = Account.getDummySenderAccount(params.getSender());
            } else {
                senderAccount = Account.getDummySenderAccountWithAlias(params.getSender());
            }

            store.updateAccount(senderAccount);
        }
        Address receiverAddress = determineReceiverAddress(params.getReceiver());
        return super.execute(
                params.getSender(),
                receiverAddress,
                gasPrice,
                params.isEstimate(),
                params.isEstimate() ? estimatedGas : params.getGas(),
                params.getValue(),
                params.getCallData(),
                params.isStatic(),
                aliasManager.resolveForEvm(params.getReceiver()),
                params.getReceiver().equals(Address.ZERO),
                params.getTracerType(),
                codeFactory);
    }

    @Override
    protected MessageFrame buildInitialFrame(
            final MessageFrame.Builder baseInitialFrame,
            final Address to,
            final Bytes payload,
            long value,
            final CodeFactory codeFactory) {
        if (Address.ZERO.equals(to)) {
            var contractAddress = EntityIdUtils.asTypedEvmAddress(entityAddressSequencer.getNewContractId(to));

            return baseInitialFrame
                    .type(MessageFrame.Type.CONTRACT_CREATION)
                    .address(contractAddress)
                    .contract(contractAddress)
                    .inputData(Bytes.EMPTY)
                    .code(codeFactory.createCode(payload, false))
                    .build();
        } else {
            final var resolvedForEvm = aliasManager.resolveForEvm(to);
            final var code = aliasManager.isMirror(resolvedForEvm) ? codeCache.getIfPresent(resolvedForEvm) : null;
            final var isNotCallingNativePrecompile = !aliasManager.isNativePrecompileAddress(resolvedForEvm);

            // If there is no bytecode, it means we have a non-token and non-contract account,
            // hence the code should be null and there must be a value transfer.
            if (!dynamicProperties.callsToNonExistingEntitiesEnabled(to)
                    && code == null
                    && value <= 0
                    && !payload.isEmpty()
                    && isNotCallingNativePrecompile) {
                throw new MirrorEvmTransactionException(
                        ResponseCodeEnum.INVALID_TRANSACTION, StringUtils.EMPTY, StringUtils.EMPTY, false);
            }

            return baseInitialFrame
                    .type(MessageFrame.Type.MESSAGE_CALL)
                    .address(to)
                    .contract(to)
                    .inputData(payload)
                    .code(code == null ? CodeV0.EMPTY_CODE : code)
                    .build();
        }
    }

    private Address determineReceiverAddress(Address receiver) {
        if (receiver == null || receiver.equals(Address.ZERO) || !aliasManager.isMirror(receiver)) {
            return receiver;
        }

        Address canonical = tokenAccessor.canonicalAddress(receiver);
        if (canonical == null || Address.ZERO.equals(canonical)) {
            return receiver;
        } else {
            return canonical;
        }
    }

    private boolean isMirror(final Address address) {
        return address == null || address.equals(Address.ZERO) || aliasManager.isMirror(address);
    }
}
