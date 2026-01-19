// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PARENT_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CREATE_CHILD;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_BYTE_CODE;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.web3j.crypto.transaction.type.TransactionType.EIP1559;
import static org.web3j.crypto.transaction.type.TransactionType.EIP2930;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.PrivateKey;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ContractResult;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.hiero.mirror.rest.model.TransactionDetail;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import org.hiero.mirror.test.e2e.acceptance.client.EthereumClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.config.Web3Properties;
import org.hiero.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import org.springframework.http.HttpStatus;
import org.web3j.crypto.transaction.type.TransactionType;

@CustomLog
@RequiredArgsConstructor
public class EthereumFeature extends AbstractEstimateFeature {

    protected final EthereumClient ethereumClient;

    protected final AccountClient accountClient;

    private final Web3Properties web3Properties;

    protected AccountId ethereumSignerAccount;
    protected PrivateKey ethereumSignerPrivateKey;
    private String account;

    private byte[] childContractBytecodeFromParent;

    private static final BigDecimal INITIAL_BALANCE = BigDecimal.valueOf(0.4); // usd

    @Given("I successfully created a signer account with an EVM address alias")
    public void createAccountWithEvmAddressAlias() {
        // Create new signer account with EVM address and ECDSA key
        var signerAccount = accountClient.createNewAccount(INITIAL_BALANCE, AccountNameEnum.BOB);
        ethereumSignerAccount = signerAccount.getAccountId();
        ethereumSignerPrivateKey = signerAccount.getPrivateKey();

        var accountInfo = mirrorClient.getAccountDetailsByAccountId(ethereumSignerAccount);
        account = accountInfo.getAccount();
    }

    @Given("I successfully create contract by Legacy ethereum transaction")
    public void createContract() {
        deployedParentContract = ethereumContractCreate(PARENT_CONTRACT);

        gasConsumedSelector = Objects.requireNonNull(mirrorClient
                .getContractInfo(deployedParentContract.contractId().toEvmAddress())
                .getBytecode());

        var txId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        verifyGasConsumed(txId, contractId, false);
    }

    @Then("the mirror node REST API should return status {int} for the eth contract creation transaction")
    public void verifyMirrorAPIContractCreationResponses(int status) {
        var mirrorTransaction = verifyEthereumContractCreate(mirrorClient, status, true);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(deployedParentContract.contractId().toString());
    }

    @Then("the mirror node REST API should return status {int} for the ethereum transaction")
    public void verifyMirrorAPIContractResponses(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(deployedParentContract.contractId().toString());
    }

    @Then("the mirror node REST API should verify the ethereum called contract function")
    public void verifyDeployedContractMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        var contractResultTimestamp = verifyContractExecutionResultsByTransactionId();
        verifyContractExecutionResults(contractResultTimestamp);
    }

    @And("the mirror node Rest API should verify the contracts have correct nonce")
    public void verifyContractNonce() {
        verifyNonceForParentContract();
        verifyNonceForChildContracts();
    }

    @Given("I successfully call function using EIP-1559 ethereum transaction")
    public void callContract() {
        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(1000));

        ExecuteContractResult executeContractResult =
                executeEthereumTransaction(deployedParentContract.contractId(), "createChild", parameters, EIP1559);

        String childAddress = executeContractResult.contractFunctionResult().getAddress(0);

        // add contract Id to the list for verification of nonce on mirror node
        assertThat(deployedParentContract.contractId().toEvmAddress()).isNotEqualTo(childAddress);
        addChildContract(childAddress);
        gasConsumedSelector = encodeDataToByteArray(PARENT_CONTRACT, CREATE_CHILD, BigInteger.valueOf(1000));

        String txId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        verifyGasConsumed(txId, contractId, true);
    }

    @Given("I successfully call function using EIP-2930 ethereum transaction")
    public void getChildBytecode() {
        var executeContractResult =
                executeEthereumTransaction(deployedParentContract.contractId(), "getBytecode", null, EIP2930);

        childContractBytecodeFromParent =
                executeContractResult.contractFunctionResult().getBytes(0);
        assertThat(childContractBytecodeFromParent).isNotNull();

        gasConsumedSelector = encodeDataToByteArray(PARENT_CONTRACT, GET_BYTE_CODE);
    }

    @And("the mirror node contract results opcodes API should return a non-empty response")
    public void verifyOpcodes() {
        if (!web3Properties.getOpcodeTracer().isEnabled()) {
            return;
        }
        log.info("Opcode tracer is enabled -> verify contract results opcodes against web3.");
        String txId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var opcodes = mirrorClient.getContractResultsOpcodes(txId);
        assertThat(opcodes).isNotNull();
        // Just verify that a list of opcodes is returned as any other static resource might get
        // quickly out of sync on EVM bumps and this would be hard to maintain and there is already a
        // stricter validation in the web3 module.
        assertThat(opcodes.getOpcodes()).isNotEmpty();
    }

    public DeployedContract ethereumContractCreate(ContractResource contractResource) {
        var resource = resourceLoader.getResource(contractResource.getPath());
        try (var in = resource.getInputStream()) {
            CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
            var fileContent = compiledSolidityArtifact.getBytecode().replaceFirst(HEX_PREFIX, "");
            var fileId = persistContractBytes(fileContent);

            networkTransactionResponse = ethereumClient.createContract(
                    ethereumSignerPrivateKey, fileId, fileContent, contractResource.getInitialBalance());
            ContractId createdContractId = verifyCreateContractNetworkResponse();
            return new DeployedContract(fileId, createdContractId, compiledSolidityArtifact);
        } catch (IOException e) {
            log.warn("Issue creating contract: {}, ex: {}", contractResource, e);
            throw new RuntimeException(e);
        }
    }

    private ContractClient.ExecuteContractResult executeEthereumTransaction(
            ContractId contractId, String functionName, ContractFunctionParameters parameters, TransactionType type) {

        ContractClient.ExecuteContractResult executeContractResult =
                ethereumClient.executeContract(ethereumSignerPrivateKey, contractId, functionName, parameters, type);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
        assertThat(executeContractResult.contractFunctionResult()).isNotNull();

        return executeContractResult;
    }

    @Override
    protected void verifyContractExecutionResults(ContractResult contractResult) {
        super.verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getFrom())
                .isEqualTo(HEX_PREFIX + AccountId.fromString(account).toEvmAddress());
    }

    protected TransactionDetail verifyEthereumContractCreate(
            MirrorNodeClient mirrorClient, int status, boolean finalizeHollowAccount) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).isNotEmpty();
        var mirrorTransaction = finalizeHollowAccount ? transactions.get(1) : transactions.get(0);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }
}
