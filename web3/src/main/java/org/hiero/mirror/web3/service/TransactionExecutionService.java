// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static org.hiero.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static org.hiero.mirror.web3.state.Utils.isMirror;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
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
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final OpcodeActionTracer opcodeActionTracer;
    private final MirrorOperationActionTracer mirrorOperationActionTracer;
    private final SystemEntity systemEntity;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public HederaEvmTransactionProcessingResult execute(final CallServiceParameters params, final long estimatedGas) {
        final var isContractCreate = params.getReceiver().isZero();
        final var configuration = mirrorNodeEvmProperties.getVersionedConfiguration();
        final var maxLifetime =
                configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        final var executor = transactionExecutorFactory.get();

        TransactionBody transactionBody;
        HederaEvmTransactionProcessingResult result = null;
        if (isContractCreate) {
            transactionBody = buildContractCreateTransactionBody(params, estimatedGas, maxLifetime);
        } else {
            transactionBody = buildContractCallTransactionBody(params, estimatedGas);
        }

        final var receipt = executor.execute(transactionBody, Instant.now(), getOperationTracers());
        final var parentTransactionStatus =
                receipt.getFirst().transactionRecord().receiptOrThrow().status();
        if (parentTransactionStatus == com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS) {
            result = buildSuccessResult(isContractCreate, receipt, params);
        } else {
            result = handleFailedResult(receipt, isContractCreate);
        }
        return result;
    }

    private ContractFunctionResult getTransactionResult(
            final TransactionRecord transactionRecord, final boolean isContractCreate) {
        return isContractCreate
                ? transactionRecord.contractCreateResultOrThrow()
                : transactionRecord.contractCallResultOrThrow();
    }

    private HederaEvmTransactionProcessingResult buildSuccessResult(
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

        return HederaEvmTransactionProcessingResult.successful(
                List.of(),
                result.gasUsed(),
                0L,
                0L,
                Bytes.wrap(result.contractCallResult().toByteArray()),
                params.getReceiver());
    }

    private HederaEvmTransactionProcessingResult handleFailedResult(
            final List<SingleTransactionRecord> transactionRecords, final boolean isContractCreate)
            throws MirrorEvmTransactionException {
        final var parentTransactionRecord = transactionRecords.getFirst().transactionRecord();
        final var result = isContractCreate
                ? parentTransactionRecord.contractCreateResult()
                : parentTransactionRecord.contractCallResult();
        final var status = parentTransactionRecord.receiptOrThrow().status().protoName();
        if (result == null) {
            // No result - the call did not reach the EVM and probably failed at pre-checks. No metric to update in this
            // case.
            throw new MirrorEvmTransactionException(status, StringUtils.EMPTY, StringUtils.EMPTY, true);
        } else {
            final var errorMessage = getErrorMessage(result).orElse(Bytes.EMPTY);
            final var detail = maybeDecodeSolidityErrorStringToReadableMessage(errorMessage);

            final var childTransactionErrors = populateChildTransactionErrors(transactionRecords);

            if (ContractCallContext.get().getOpcodeTracerOptions() == null) {
                var processingResult = HederaEvmTransactionProcessingResult.failed(
                        result.gasUsed(), 0L, 0L, Optional.of(errorMessage), Optional.empty());

                throw new MirrorEvmTransactionException(
                        status, detail, errorMessage.toHexString(), processingResult, true, childTransactionErrors);
            } else {
                // If we are in an opcode trace scenario, we need to return a failed result in order to get the
                // opcode list from the ContractCallContext. If we throw an exception instead of returning a result,
                // as in the regular case, we won't be able to get the opcode list.
                return HederaEvmTransactionProcessingResult.failed(
                        result.gasUsed(), 0L, 0L, Optional.of(errorMessage), Optional.empty());
            }
        }
    }

    private Optional<Bytes> getErrorMessage(final ContractFunctionResult result) {
        return result.errorMessage().startsWith(HEX_PREFIX)
                ? Optional.of(Bytes.fromHexString(result.errorMessage()))
                : Optional.empty(); // If it doesn't start with 0x, the message is already decoded and readable.
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

    private ProtoBytes convertAddressToProtoBytes(final Address address) {
        return ProtoBytes.newBuilder()
                .value(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(address.toArrayUnsafe()))
                .build();
    }

    private AccountID getSenderAccountID(final CallServiceParameters params) {
        // Set a default account to keep the sender parameter optional.
        if (params.getSender().canonicalAddress().isZero() && params.getValue() == 0L) {
            return EntityIdUtils.toAccountId(systemEntity.treasuryAccount());
        }
        final var senderAddress = params.getSender().canonicalAddress();
        final var accountIDNum = getSenderAccountIDAsNum(senderAddress);

        final var account = accountReadableKVState.get(accountIDNum);
        if (account == null) {
            throwPayerAccountNotFoundException(SENDER_NOT_FOUND);
        } else if (account.smartContract()) {
            throwPayerAccountNotFoundException(SENDER_IS_SMART_CONTRACT);
        }

        return accountIDNum;
    }

    private AccountID getSenderAccountIDAsNum(final Address senderAddress) {
        AccountID accountIDNum;
        if (!isMirror(senderAddress)) {
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
        throw new MirrorEvmTransactionException(
                ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND, message, StringUtils.EMPTY, true);
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
            if (status == com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS) {
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
