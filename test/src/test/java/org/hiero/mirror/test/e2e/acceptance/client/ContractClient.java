// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractCallQuery;
import com.hedera.hashgraph.sdk.ContractCreateTransaction;
import com.hedera.hashgraph.sdk.ContractDeleteTransaction;
import com.hedera.hashgraph.sdk.ContractExecuteTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.ContractUpdateTransaction;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.TransactionRecord;
import jakarta.inject.Named;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.retry.support.RetryTemplate;

@Named
public class ContractClient extends AbstractNetworkClient {

    private final Map<String, ContractId> contractIds = new ConcurrentHashMap<>();

    public ContractClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    @Override
    public void clean() {
        if (Boolean.parseBoolean(System.getenv("HIERO_MIRROR_TEST_ACCEPTANCE_SKIP_CLEANUP_ENTITIES"))) {
            // In CI we don't want to cleanup as the entities are needed in the k6 test in the next step.
            log.warn("Acceptance tests running in CI -> skip cleanup.");
            for (var contractName : contractIds.keySet()) {
                log.info("Skipping cleanup of contract [" + contractName + "] at address "
                        + contractIds.get(contractName).toEvmAddress());
            }
            return;
        }

        log.info("Deleting {} contracts", contractIds.size());
        deleteAll(contractIds.values(), id -> deleteContract(id, client.getOperatorAccountId(), null));
    }

    public NetworkTransactionResponse createContract(
            String contractName,
            FileId fileId,
            long gas,
            Hbar payableAmount,
            ContractFunctionParameters contractFunctionParameters) {
        var memo = getMemo(String.format("Create contract %s", contractName));
        ContractCreateTransaction contractCreateTransaction = new ContractCreateTransaction()
                .setAdminKey(sdkClient.getExpandedOperatorAccountId().getPublicKey())
                .setBytecodeFileId(fileId)
                .setContractMemo(memo)
                .setGas(gas)
                .setTransactionMemo(memo);

        if (contractFunctionParameters != null) {
            contractCreateTransaction.setConstructorParameters(contractFunctionParameters);
        }

        if (payableAmount != null) {
            contractCreateTransaction.setInitialBalance(payableAmount);
        }

        var response = executeTransactionAndRetrieveReceipt(contractCreateTransaction);
        var contractId = response.getReceipt().contractId;
        log.info("Created new contract {} with memo '{}' via {}", contractId, memo, response.getTransactionId());

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult("constructor", transactionRecord.contractFunctionResult);
        contractIds.put(contractName, contractId);

        return response;
    }

    public NetworkTransactionResponse updateContract(ContractId contractId) {
        var memo = getMemo("Update contract");
        ContractUpdateTransaction contractUpdateTransaction = new ContractUpdateTransaction()
                .setContractId(contractId)
                .setContractMemo(memo)
                .setTransactionMemo(memo);

        var response = executeTransactionAndRetrieveReceipt(contractUpdateTransaction);
        log.info("Updated contract {} with memo '{}' via {}", contractId, memo, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteContract(
            ContractId contractId, AccountId transferAccountId, ContractId transferContractId) {
        var memo = getMemo("Delete contract");
        ContractDeleteTransaction contractDeleteTransaction =
                new ContractDeleteTransaction().setContractId(contractId).setTransactionMemo(memo);

        // either AccountId or ContractId, not both
        if (transferAccountId != null) {
            contractDeleteTransaction.setTransferAccountId(transferAccountId);
        }

        if (transferContractId != null) {
            contractDeleteTransaction.setTransferContractId(transferContractId);
        }

        var response = executeTransactionAndRetrieveReceipt(contractDeleteTransaction);
        log.info("Deleted contract {} via {}", contractId, response.getTransactionId());
        contractIds.remove(contractId);
        return response;
    }

    public ExecuteContractResult executeContract(
            ContractId contractId,
            long gas,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount) {

        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(gas)
                .setTransactionMemo(getMemo("Execute contract"));

        if (parameters == null) {
            contractExecuteTransaction.setFunction(functionName);
        } else {
            contractExecuteTransaction.setFunction(functionName, parameters);
        }

        if (payableAmount != null) {
            contractExecuteTransaction.setPayableAmount(payableAmount);
        }

        var response = executeTransactionAndRetrieveReceipt(contractExecuteTransaction);

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        log.info("Called contract {} function {} via {}", contractId, functionName, response.getTransactionId());
        return new ExecuteContractResult(transactionRecord.contractFunctionResult, response);
    }

    public ExecuteContractResult executeContract(
            ContractId contractId, long gas, String functionName, byte[] parameters, Hbar payableAmount) {

        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(gas)
                .setTransactionMemo(getMemo("Execute contract"));

        if (parameters == null) {
            contractExecuteTransaction.setFunction(functionName);
        } else {
            contractExecuteTransaction.setFunctionParameters(ByteString.copyFrom(parameters));
        }

        if (payableAmount != null) {
            contractExecuteTransaction.setPayableAmount(payableAmount);
        }

        var response = executeTransactionAndRetrieveReceipt(contractExecuteTransaction);

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        log.info("Called contract {} function {} via {}", contractId, functionName, response.getTransactionId());
        return new ExecuteContractResult(transactionRecord.contractFunctionResult, response);
    }

    public ContractFunctionResult executeContractQuery(
            ContractId contractId, String functionName, Long gas, byte[] data)
            throws PrecheckStatusException, TimeoutException {
        ContractCallQuery contractCallQuery =
                new ContractCallQuery().setContractId(contractId).setGas(gas);

        contractCallQuery.setFunctionParameters(data);

        long costInTinybars = contractCallQuery.getCost(client).toTinybars();

        long additionalTinybars = 10000;
        long totalPaymentInTinybars = costInTinybars + additionalTinybars;

        contractCallQuery.setQueryPayment(Hbar.fromTinybars(totalPaymentInTinybars));

        ContractFunctionResult functionResult = contractCallQuery.execute(client);

        log.info("Executed query on contract {} function {}, result: {}", contractId, functionName, functionResult);

        return functionResult;
    }

    public String getClientAddress() {
        return sdkClient.getClient().getOperatorAccountId().toEvmAddress();
    }

    private void logContractFunctionResult(String functionName, ContractFunctionResult contractFunctionResult) {
        if (contractFunctionResult == null) {
            return;
        }

        log.trace(
                "ContractFunctionResult for function {}, contractId: {}, gasUsed: {}, logCount: {}",
                functionName,
                contractFunctionResult.contractId,
                contractFunctionResult.gasUsed,
                contractFunctionResult.logs.size());
    }

    @RequiredArgsConstructor
    public enum NodeNameEnum {
        CONSENSUS("consensus"),
        MIRROR("mirror");

        private final String name;

        static Optional<NodeNameEnum> of(String name) {
            try {
                return Optional.ofNullable(name).map(NodeNameEnum::valueOf);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    public record ExecuteContractResult(
            ContractFunctionResult contractFunctionResult, NetworkTransactionResponse networkTransactionResponse) {}
}
