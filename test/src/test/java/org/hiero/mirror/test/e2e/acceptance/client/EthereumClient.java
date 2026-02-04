// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.ContractExecuteTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.EthereumTransaction;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TransactionRecord;
import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryTemplate;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.utils.Numeric;

@Named
public class EthereumClient extends AbstractNetworkClient {
    @Autowired
    private AcceptanceTestProperties acceptanceTestProperties;

    private final Map<PrivateKey, BigInteger> accountNonce = new ConcurrentHashMap<>();

    public EthereumClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        super(sdkClient, retryTemplate, acceptanceTestProperties);
    }

    @Override
    public void clean() {
        // Contracts created by ethereum transactions are immutable
        log.info("Can't delete contracts created by ethereum transactions");
    }

    protected BigInteger maxContractFunctionGas() {
        return BigInteger.valueOf(
                acceptanceTestProperties.getFeatureProperties().getMaxContractFunctionGas());
    }

    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);

    private final BigInteger maxFeePerGas = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));

    private final BigInteger gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));

    public NetworkTransactionResponse createContract(
            PrivateKey signerKey, FileId fileId, String fileContents, long initialBalance) {

        var value = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(initialBalance));

        var rawTransaction = RawTransaction.createTransaction(
                getNonce(signerKey), gasPrice, maxContractFunctionGas(), "", value, fileContents);
        Credentials credentials = Credentials.create(signerKey.toStringRaw());
        var signedTransaction = TransactionEncoder.signMessage(rawTransaction, credentials);

        EthereumTransaction ethereumTransaction = new EthereumTransaction()
                .setCallDataFileId(fileId)
                .setMaxGasAllowanceHbar(Hbar.from(100L))
                .setEthereumData(signedTransaction);

        var memo = getMemo("Create contract");

        var response = executeTransactionAndRetrieveReceipt(ethereumTransaction, null, null);
        var contractId = response.getReceipt().contractId;
        log.info("Created new contract {} with memo '{}' via {}", contractId, memo, response.getTransactionId());

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult("constructor", transactionRecord.contractFunctionResult);
        return response;
    }

    public ContractClient.ExecuteContractResult executeContract(
            PrivateKey signerKey,
            ContractId contractId,
            String functionName,
            ContractFunctionParameters functionParameters,
            TransactionType type) {

        var callData = buildCallDataAsHexedString(functionName, functionParameters);
        var value = BigInteger.ZERO;

        // build raw transaction
        var rawTransaction =
                switch (type) {
                    case EIP1559 ->
                        RawTransaction.createTransaction(
                                acceptanceTestProperties.getNetwork().getChainId(),
                                getNonce(signerKey),
                                maxContractFunctionGas(),
                                contractId.toEvmAddress(),
                                value,
                                callData,
                                BigInteger.valueOf(20000L), // maxPriorityGas
                                maxFeePerGas);
                    case EIP2930 ->
                        RawTransaction.createTransaction(
                                acceptanceTestProperties.getNetwork().getChainId(),
                                getNonce(signerKey),
                                maxContractFunctionGas(),
                                contractId.toEvmAddress(),
                                value,
                                callData,
                                BigInteger.valueOf(20000L), // maxPriorityGas
                                maxFeePerGas,
                                Collections.emptyList());
                    default ->
                        RawTransaction.createTransaction(
                                getNonce(signerKey), gasPrice, maxContractFunctionGas(), "", value, callData);
                };

        // sign and execute transaction
        Credentials credentials = Credentials.create(signerKey.toStringRaw());
        EthereumTransaction ethereumTransaction = new EthereumTransaction()
                .setMaxGasAllowanceHbar(Hbar.from(100L))
                .setEthereumData(TransactionEncoder.signMessage(rawTransaction, credentials));

        var response = executeTransactionAndRetrieveReceipt(ethereumTransaction, null, null);

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        log.info("Called contract {} function {} via {}", contractId, functionName, response.getTransactionId());
        return new ContractClient.ExecuteContractResult(transactionRecord.contractFunctionResult, response);
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

    private BigInteger getNonce(PrivateKey accountKey) {
        return accountNonce.merge(accountKey, BigInteger.ONE, BigInteger::add).subtract(BigInteger.ONE);
    }

    private String buildCallDataAsHexedString(String functionName, ContractFunctionParameters functionParameters) {
        var parameters = functionParameters != null ? functionParameters : new ContractFunctionParameters();
        var encodedParameters = new ContractExecuteTransaction()
                .setFunction(functionName, parameters)
                .getFunctionParameters();
        return Numeric.toHexString(encodedParameters.toByteArray());
    }
}
