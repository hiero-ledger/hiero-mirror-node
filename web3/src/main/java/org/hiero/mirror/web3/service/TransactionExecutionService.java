// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.hapi.utils.keys.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static org.hiero.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static org.hiero.mirror.web3.state.Utils.DEFAULT_KEY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.MirrorOperationActionTracer;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeActionTracer;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@Named
@CustomLog
@RequiredArgsConstructor
public class TransactionExecutionService {

    private static final Duration TRANSACTION_DURATION = new Duration(15);
    private static final long CONTRACT_CREATE_TX_FEE = 100_000_000L;
    private static final String SENDER_NOT_FOUND = "Sender account not found.";
    private static final String SENDER_IS_SMART_CONTRACT = "Sender account is a smart contract.";

    private final AccountReadableKVState accountReadableKVState;
    private final AliasesReadableKVState aliasesReadableKVState;
    private final CommonProperties commonProperties;
    private final EvmProperties evmProperties;
    private final OpcodeActionTracer opcodeActionTracer;
    private final MirrorOperationActionTracer mirrorOperationActionTracer;
    private final SystemEntity systemEntity;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public EvmTransactionResult execute(final CallServiceParameters params, final long estimatedGas) {
        final var isContractCreate = params.getReceiver().isZero();
        final var configuration = evmProperties.getVersionedConfiguration();
        final var maxLifetime =
                configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        final var executor = transactionExecutorFactory.get();

        TransactionBody transactionBody;
        EvmTransactionResult result;
        final var ethereumData = params.getEthereumData();
        if (ethereumData != null && !ethereumData.isEmpty()) {
            transactionBody = buildEthereumTransactionBody(params);
        } else if (isContractCreate) {
            transactionBody = buildContractCreateTransactionBody(params, estimatedGas, maxLifetime);
        } else {
            transactionBody = buildContractCallTransactionBody(params, estimatedGas);
        }

        final var singleTransactionRecords = executor.execute(transactionBody, Instant.now(), getOperationTracers());
        final var parentTransactionStatus = singleTransactionRecords
                .getFirst()
                .transactionRecord()
                .receiptOrThrow()
                .status();
        if (parentTransactionStatus == SUCCESS) {
            result = buildSuccessResult(isContractCreate, singleTransactionRecords, params);
        } else {
            result = handleFailedResult(singleTransactionRecords, isContractCreate);
        }
        return result;
    }

    private ContractFunctionResult getTransactionResult(
            final TransactionRecord transactionRecord, final boolean isContractCreate) {
        return isContractCreate
                ? transactionRecord.contractCreateResultOrThrow()
                : transactionRecord.contractCallResultOrThrow();
    }

    private EvmTransactionResult buildSuccessResult(
            final boolean isContractCreate,
            final List<SingleTransactionRecord> transactionRecords,
            final CallServiceParameters params) {
        final var parentTransaction = transactionRecords.getFirst().transactionRecord();
        final var childTransactionErrors = populateChildTransactionErrors(transactionRecords);

        final var result = getTransactionResult(parentTransaction, isContractCreate);

        if (!childTransactionErrors.isEmpty()) {
            // there are some child transactions that failed but parent is SUCCESS, logging a warning
            final var contractId = result.contractID();
            log.warn(
                    "Child transaction errors present for contract: {} with successful parent transaction, errors: {}",
                    contractId.hasContractNum() ? contractId.contractNum() : contractId.evmAddress(),
                    childTransactionErrors);
        }

        return new EvmTransactionResult(parentTransaction.receipt().status(), result);
    }

    private EvmTransactionResult handleFailedResult(
            final List<SingleTransactionRecord> transactionRecords, final boolean isContractCreate)
            throws MirrorEvmTransactionException {
        final var parentTransactionRecord = transactionRecords.getFirst().transactionRecord();
        final var result = isContractCreate
                ? parentTransactionRecord.contractCreateResult()
                : parentTransactionRecord.contractCallResult();
        final var status = parentTransactionRecord.receiptOrThrow().status();
        if (result == null) {
            // No result - the call did not reach the EVM and probably failed at pre-checks. No metric to update in this
            // case.
            throw new MirrorEvmTransactionException(status, StringUtils.EMPTY, StringUtils.EMPTY);
        } else {
            final var childTransactionErrors = populateChildTransactionErrors(transactionRecords);

            if (ContractCallContext.get().getOpcodeTracerOptions() == null) {
                var processingResult = new EvmTransactionResult(status, result);

                final var errorMessage = processingResult.getErrorMessage().orElse(Bytes.EMPTY);
                final var detail = maybeDecodeSolidityErrorStringToReadableMessage(errorMessage);
                throw new MirrorEvmTransactionException(
                        status, detail, errorMessage.toHexString(), processingResult, childTransactionErrors);
            } else {
                // If we are in an opcode trace scenario, we need to return a failed result in order to get the
                // opcode list from the ContractCallContext. If we throw an exception instead of returning a result,
                // as in the regular case, we won't be able to get the opcode list.
                return new EvmTransactionResult(status, result);
            }
        }
    }

    private TransactionBody.Builder defaultTransactionBodyBuilder(final CallServiceParameters params) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(new Timestamp(Instant.now().getEpochSecond(), 0))
                        .accountID(getSenderAccountID(params))
                        .build())
                .nodeAccountID(EntityIdUtils.toAccountId(systemEntity.treasuryAccount()))
                .transactionValidDuration(TRANSACTION_DURATION);
    }

    private TransactionBody buildContractCreateTransactionBody(
            final CallServiceParameters params, final long estimatedGas, final long maxLifetime) {
        return defaultTransactionBodyBuilder(params)
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .initcode(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArrayUnsafe()))
                        .gas(estimatedGas)
                        .autoRenewPeriod(new Duration(maxLifetime))
                        .build())
                .transactionFee(CONTRACT_CREATE_TX_FEE)
                .build();
    }

    private TransactionBody buildContractCallTransactionBody(
            final CallServiceParameters params, final long estimatedGas) {
        return defaultTransactionBodyBuilder(params)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .contractID(ContractID.newBuilder()
                                .shardNum(commonProperties.getShard())
                                .realmNum(commonProperties.getRealm())
                                .evmAddress(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                        params.getReceiver().toArrayUnsafe()))
                                .build())
                        .functionParameters(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArrayUnsafe()))
                        .amount(params.getValue()) // tinybars sent to contract
                        .gas(estimatedGas)
                        .build())
                .build();
    }

    private TransactionBody buildEthereumTransactionBody(final CallServiceParameters params) {
        final var ethereumTransactionBuilder = EthereumTransactionBody.newBuilder()
                .ethereumData(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                        params.getEthereumData().toArray()));
        return defaultTransactionBodyBuilder(params)
                .ethereumTransaction(ethereumTransactionBuilder.build())
                .build();
    }

    private ProtoBytes convertAddressToProtoBytes(final Address address) {
        return ProtoBytes.newBuilder()
                .value(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(address.toArrayUnsafe()))
                .build();
    }

    private AccountID getSenderAccountID(final CallServiceParameters params) {
        // Set a default account to keep the sender parameter optional.
        if (params.getSender().isZero() && params.getValue() == 0L) {
            return EntityIdUtils.toAccountId(systemEntity.treasuryAccount());
        }
        final var senderAddress = params.getSender();
        final var accountIDNum = getSenderAccountIDAsNum(senderAddress);

        final var account = accountReadableKVState.get(accountIDNum);
        if (account == null) {
            throwPayerAccountNotFoundException(SENDER_NOT_FOUND);
        } else if (account.smartContract()) {
            throwPayerAccountNotFoundException(SENDER_IS_SMART_CONTRACT);
        } else if (!account.hasKey() || account.key().equals(IMMUTABILITY_SENTINEL_KEY)) {
            // If the account is hollow, complete it in the state as a workaround
            // as this happens in HandleWorkflow in hedera-app but calling the
            // transaction executor directly skips this account completion and
            // this results in failed transactions that would otherwise succeed
            // against the consensus node.
            final var writableAccountCache =
                    ContractCallContext.get().getWriteCacheState(AccountReadableKVState.STATE_ID);
            final var completedAccount = account.copyBuilder().key(DEFAULT_KEY).build();
            writableAccountCache.put(account.accountId(), completedAccount);
        }

        return accountIDNum;
    }

    private AccountID getSenderAccountIDAsNum(final Address senderAddress) {
        AccountID accountIDNum;
        if (senderAddress != null && !ConversionUtils.isLongZero(senderAddress)) {
            // If the address is an alias we need to first check if it exists and get the AccountID as a num.
            accountIDNum = aliasesReadableKVState.get(convertAddressToProtoBytes(senderAddress));
            if (accountIDNum == null) {
                throwPayerAccountNotFoundException(SENDER_NOT_FOUND);
            }
        } else {
            final var senderAccountID = accountIdFromEvmAddress(senderAddress);
            // If the address was passed as a long-zero address we need to convert it to the correct AccountID type.
            accountIDNum = AccountID.newBuilder()
                    .accountNum(senderAccountID.getAccountNum())
                    .shardNum(senderAccountID.getShardNum())
                    .realmNum(senderAccountID.getRealmNum())
                    .build();
        }
        return accountIDNum;
    }

    // In services SolvencyPreCheck#getPayerAccount() in case the payer account is not found or is a smart contract the
    // error response that is returned is PAYER_ACCOUNT_NOT_FOUND, so we use it in here for consistency.
    private void throwPayerAccountNotFoundException(final String message) {
        throw new MirrorEvmTransactionException(PAYER_ACCOUNT_NOT_FOUND, message, StringUtils.EMPTY);
    }

    private OperationTracer[] getOperationTracers() {
        return ContractCallContext.get().getOpcodeTracerOptions() != null
                ? new OperationTracer[] {opcodeActionTracer}
                : new OperationTracer[] {mirrorOperationActionTracer};
    }

    private SequencedCollection<String> populateChildTransactionErrors(
            List<SingleTransactionRecord> singleTransactionRecords) {
        SequencedCollection<String> childTransactionErrors = null;

        // skipping parent transaction
        final var iterator = singleTransactionRecords.listIterator(1);
        while (iterator.hasNext()) {
            final var record = iterator.next().transactionRecord();

            final var status = record.receiptOrThrow().status();
            if (status == SUCCESS || status == REVERTED_SUCCESS) {
                continue;
            }

            if (childTransactionErrors == null) {
                childTransactionErrors = new LinkedHashSet<>();
            }

            childTransactionErrors.add(status.protoName());
        }

        return childTransactionErrors != null ? childTransactionErrors : List.of();
    }
}
