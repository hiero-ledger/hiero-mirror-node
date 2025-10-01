// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.rest.model.TransactionTypes.CONTRACTCALL;
import static org.hiero.mirror.rest.model.TransactionTypes.CONTRACTCREATEINSTANCE;
import static org.hiero.mirror.rest.model.TransactionTypes.CRYPTOCREATEACCOUNT;
import static org.hiero.mirror.rest.model.TransactionTypes.CRYPTOTRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.rest.model.ContractResponse;
import org.hiero.mirror.rest.model.ContractResult;
import org.hiero.mirror.rest.model.TransactionDetail;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.config.Web3Properties;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.hiero.mirror.test.e2e.acceptance.util.env.EnvVarWriter;
import org.hiero.mirror.test.e2e.acceptance.util.env.K6EnvProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

@RequiredArgsConstructor
public class ContractFeature extends BaseContractFeature {
    private static final String GET_ACCOUNT_BALANCE_SELECTOR = "6896fabf";
    private static final String GET_SENDER_SELECTOR = "5e01eb5a";
    private static final String MULTIPLY_SIMPLE_NUMBERS_SELECTOR = "8070450f";
    private static final String IDENTIFIER_SELECTOR = "7998a1c4";
    private static final String WRONG_SELECTOR = "000000";
    private static final String ACCOUNT_EMPTY_KEYLIST = "3200";
    private static final int EVM_ADDRESS_SALT = 42;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final Web3Properties web3Properties;
    private final CommonProperties commonProperties;
    private final EnvVarWriter envVarWriter;

    private String create2ChildContractEvmAddress;
    private String create2ChildContractEntityId;
    private AccountId create2ChildContractAccountId;
    private ContractId create2ChildContractContractId;

    @Value("classpath:solidity/artifacts/contracts/Parent.sol/Parent.json")
    private Resource parentContract;

    private byte[] childContractBytecodeFromParent;

    @Given("I successfully create a contract from the parent contract bytes with 10000000 balance")
    public void createNewContract() {
        deployedParentContract = getContract(ContractResource.PARENT_CONTRACT);
        envVarWriter.appendParameter(
                K6EnvProperties.DEFAULT_CONTRACT_ADDRESS,
                deployedParentContract.contractId().toEvmAddress());
    }

    @Given("I successfully call the contract")
    public void callContract() {
        // log and results to be verified
        executeCreateChildTransaction(1000);
    }

    @Given("I successfully update the contract")
    public void updateContract() {
        networkTransactionResponse = contractClient.updateContract(deployedParentContract.contractId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully delete the parent contract")
    public void deleteParentContract() {
        networkTransactionResponse = contractClient.deleteContract(
                deployedParentContract.contractId(),
                contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId(),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully delete the parent contract bytecode file")
    public void deleteParentContractFile() {
        networkTransactionResponse = fileClient.deleteFile(deployedParentContract.fileId());

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int} for the contract transaction")
    public void verifyMirrorAPIContractResponses(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(deployedParentContract.contractId().toString());
    }

    @Then("the mirror node REST API should return status {int} for the self destruct transaction")
    public void verifyMirrorAPIContractChildSelfDestructResponses(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(create2ChildContractEntityId);
    }

    @And("the mirror node REST API should return status {int} for the account transaction")
    public void verifyMirrorAPIAccountResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Then("the mirror node REST API should verify the contract")
    public void verifyContract() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        var contractResultTimestamp = verifyContractExecutionResultsByTransactionId();
        verifyContractExecutionResultByIdAndTimestamp(contractResultTimestamp);
    }

    @Then("the mirror node REST API should verify the updated contract entity")
    public void verifyUpdatedContractMirror() {
        verifyContractFromMirror(false);
    }

    @Then("I call the contract via the mirror node REST API")
    public void restContractCall() {
        if (!web3Properties.isEnabled()) {
            return;
        }

        var from = contractClient.getClientAddress();
        var to = deployedParentContract.contractId().toEvmAddress();

        var contractCallRequestGetAccountBalance = ModelBuilder.contractCallRequest()
                .data(GET_ACCOUNT_BALANCE_SELECTOR)
                .from(from)
                .to(to);
        var getAccountBalanceResponse = callContract(contractCallRequestGetAccountBalance);
        assertThat(getAccountBalanceResponse.getResultAsNumber()).isEqualTo(1000L);

        var contractCallRequestGetSender = ModelBuilder.contractCallRequest()
                .data(GET_SENDER_SELECTOR)
                .from(from)
                .to(to);
        var getSenderResponse = callContract(contractCallRequestGetSender);
        assertThat(getSenderResponse.getResultAsAddress()).isEqualTo(from);

        var contractCallMultiplySimpleNumbers = ModelBuilder.contractCallRequest()
                .data(MULTIPLY_SIMPLE_NUMBERS_SELECTOR)
                .from(from)
                .to(to);
        var multiplySimpleNumbersResponse = callContract(contractCallMultiplySimpleNumbers);
        assertThat(multiplySimpleNumbersResponse.getResultAsNumber()).isEqualTo(4L);

        var contractCallIdentifier = ModelBuilder.contractCallRequest()
                .data(IDENTIFIER_SELECTOR)
                .from(from)
                .to(to);
        var identifierResponse = callContract(contractCallIdentifier);
        assertThat(identifierResponse.getResultAsSelector()).isEqualTo(IDENTIFIER_SELECTOR);

        var contractCallWrongSelector = ModelBuilder.contractCallRequest()
                .data(WRONG_SELECTOR)
                .from(from)
                .to(to);
        assertThatThrownBy(() -> callContract(contractCallWrongSelector))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Then("the mirror node REST API should verify the deleted contract entity")
    public void verifyDeletedContractMirror() {
        verifyContractFromMirror(true);

        var parentContract =
                mirrorClient.getContracts(deployedParentContract.contractId().toString());
        assertThat(parentContract.getContracts().getFirst().getDeleted()).isTrue();
    }

    @Given("I call the parent contract to retrieve child contract bytecode")
    public void getChildContractBytecode() {
        var executeContractResult = executeGetChildContractBytecodeTransaction();
        childContractBytecodeFromParent =
                executeContractResult.contractFunctionResult().getBytes(0);
        assertNotNull(childContractBytecodeFromParent);
    }

    @When("I call the parent contract evm address function with the bytecode of the child contract")
    public void getCreate2ChildContractEvmAddress() {
        var executeContractResult = executeGetEvmAddressTransaction(EVM_ADDRESS_SALT);
        create2ChildContractEvmAddress =
                executeContractResult.contractFunctionResult().getAddress(0);
        // Add child contract to verify nonce later
        addChildContract(create2ChildContractEvmAddress);
        create2ChildContractAccountId = AccountId.fromEvmAddress(
                create2ChildContractEvmAddress, commonProperties.getShard(), commonProperties.getRealm());
        create2ChildContractContractId = ContractId.fromEvmAddress(
                commonProperties.getShard(), commonProperties.getRealm(), create2ChildContractEvmAddress);
    }

    @And("I create a hollow account using CryptoTransfer of {int} to the evm address")
    public void createHollowAccountWithCryptoTransfertoEvmAddress(int amount) {
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(create2ChildContractAccountId, Hbar.fromTinybars(amount), null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @And("the mirror node REST API should verify the account receiving {int} is hollow")
    public void verifyMirrorAPIHollowAccountResponse(int amount) {
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(create2ChildContractAccountId);
        create2ChildContractEntityId = mirrorAccountResponse.getAccount();

        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(TransactionDetail::getConsensusTimestamp))
                .toList();

        assertEquals(2, transactions.size());
        assertEquals(CRYPTOCREATEACCOUNT, transactions.get(0).getName());
        assertEquals(CRYPTOTRANSFER, transactions.get(1).getName());

        assertNotNull(mirrorAccountResponse.getAccount());
        assertEquals(amount, mirrorAccountResponse.getBalance().getBalance());
        // Hollow account indicated by not having a public key defined.
        assertThat(mirrorAccountResponse.getKey()).isNull();
    }

    @And("the mirror node REST API should indicate not found when using evm address to retrieve as a contract")
    public void verifyMirrorAPIContractNotFoundResponse() {
        try {
            mirrorClient.getContractInfo(create2ChildContractEvmAddress);
            fail("Did not expect to find contract at EVM address");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @When("I create a child contract by calling parent contract function to deploy using CREATE2")
    public void createChildContractUsingCreate2() {
        executeCreate2Transaction(EVM_ADDRESS_SALT);
    }

    @And("the mirror node REST API should retrieve the child contract when using evm address")
    public void verifyMirrorAPIContractFoundResponse() {
        var mirrorContractResponse = mirrorClient.getContractInfo(create2ChildContractEvmAddress);
        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(TransactionDetail::getConsensusTimestamp))
                .toList();

        assertNotNull(transactions);
        assertEquals(2, transactions.size());
        assertEquals(
                deployedParentContract.contractId().toString(),
                transactions.get(0).getEntityId());
        assertEquals(CONTRACTCALL, transactions.get(0).getName());
        assertEquals(create2ChildContractEntityId, transactions.get(1).getEntityId());
        assertEquals(CONTRACTCREATEINSTANCE, transactions.get(1).getName());

        String childContractBytecodeFromParentHex = HexFormat.of().formatHex(childContractBytecodeFromParent);
        assertEquals(
                childContractBytecodeFromParentHex,
                mirrorContractResponse.getBytecode().replaceFirst(HEX_PREFIX, ""));
        assertEquals(
                create2ChildContractEvmAddress,
                mirrorContractResponse.getEvmAddress().replaceFirst(HEX_PREFIX, ""));
    }

    @And("the mirror node REST API should verify the account is no longer hollow")
    public void verifyMirrorAPIFullAccountResponse() {
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(create2ChildContractAccountId);
        assertNotNull(mirrorAccountResponse.getAccount());
        assertNotEquals(ACCOUNT_EMPTY_KEYLIST, mirrorAccountResponse.getKey().getKey());
    }

    @And("the mirror node Rest API should verify the parent contract has correct nonce")
    public void verifyContractNonce() {
        verifyNonceForParentContract();
        verifyNonceForChildContracts();
    }

    @When("I successfully delete the child contract by calling it and causing it to self destruct")
    public void deleteChildContractUsingSelfDestruct() {
        executeSelfDestructTransaction();
    }

    @And("the mirror node contract logs REST API should return a non-empty response")
    public void checkContractLogs() {
        var contractLogsPerSingleContract = mirrorClient.getContractLogsByContractId(
                deployedParentContract.contractId().toString());
        assertThat(contractLogsPerSingleContract.getLogs()).isNotNull().isNotEmpty();

        var contractLogs = mirrorClient.getContractLogs(
                contractLogsPerSingleContract.getLogs().getFirst().getTimestamp());
        assertThat(contractLogs.getLogs())
                .isNotNull()
                .isNotEmpty()
                .containsAnyElementsOf(contractLogsPerSingleContract.getLogs());
        assertThat(contractLogsPerSingleContract.getLogs())
                .contains(contractLogs.getLogs().getFirst());
    }

    @Override
    protected ContractResponse verifyContractFromMirror(boolean isDeleted) {
        var mirrorContract = super.verifyContractFromMirror(isDeleted);

        assertThat(mirrorContract.getAdminKey()).isNotNull();
        assertThat(mirrorContract.getAdminKey().getKey())
                .isEqualTo(contractClient
                        .getSdkClient()
                        .getExpandedOperatorAccountId()
                        .getPublicKey()
                        .toStringRaw());
        return mirrorContract;
    }

    private boolean isEmptyHex(String hexString) {
        return !StringUtils.hasLength(hexString) || hexString.equals(HEX_PREFIX);
    }

    @Override
    protected void verifyContractExecutionResults(ContractResult contractResult) {
        super.verifyContractExecutionResults(contractResult);

        ContractExecutionStage contractExecutionStage = isEmptyHex(contractResult.getFunctionParameters())
                ? ContractExecutionStage.CREATION
                : ContractExecutionStage.CALL;

        assertThat(contractResult.getFrom())
                .isEqualTo(HEX_PREFIX
                        + contractClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toEvmAddress());

        var createdIds = contractResult.getCreatedContractIds();
        assertThat(createdIds).isNotEmpty();

        int amount = 0; // no payment in contract construction phase
        int numCreatedIds = 2; // parent and child contract
        switch (contractExecutionStage) {
            case CREATION:
                amount = 10000000;
                assertThat(createdIds)
                        .contains(deployedParentContract.contractId().toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isTrue();
                break;
            case CALL:
                numCreatedIds = 1;
                assertThat(createdIds)
                        .doesNotContain(deployedParentContract.contractId().toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isFalse();
                break;
            default:
                break;
        }

        assertThat(contractResult.getAmount()).isEqualTo(amount);
        assertThat(createdIds).hasSize(numCreatedIds);
    }

    private void executeCreateChildTransaction(int transferAmount) {
        ContractFunctionParameters parameters =
                new ContractFunctionParameters().addUint256(BigInteger.valueOf(transferAmount));

        ExecuteContractResult executeContractResult =
                executeContractCallTransaction(deployedParentContract.contractId(), "createChild", parameters, null);
        String childAddress = executeContractResult.contractFunctionResult().getAddress(0);

        // add contract Id to the list for verification of nonce on mirror node
        addChildContract(childAddress);
    }

    private ExecuteContractResult executeGetChildContractBytecodeTransaction() {
        return executeContractCallTransaction(deployedParentContract.contractId(), "getBytecode", null, null);
    }

    private ExecuteContractResult executeGetEvmAddressTransaction(int salt) {
        ContractFunctionParameters parameters = new ContractFunctionParameters()
                .addBytes(childContractBytecodeFromParent)
                .addUint256(BigInteger.valueOf(salt));

        return executeContractCallTransaction(deployedParentContract.contractId(), "getAddress", parameters, null);
    }

    private ExecuteContractResult executeCreate2Transaction(int salt) {
        ContractFunctionParameters parameters = new ContractFunctionParameters()
                .addBytes(childContractBytecodeFromParent)
                .addUint256(BigInteger.valueOf(salt));

        return executeContractCallTransaction(deployedParentContract.contractId(), "create2Deploy", parameters, null);
    }

    // This is a function call on the CREATE2 created child contract, not the parent.
    private ExecuteContractResult executeSelfDestructTransaction() {
        return executeContractCallTransaction(create2ChildContractContractId, "vacateAddress", null, null);
    }

    private ExecuteContractResult executeContractCallTransaction(
            ContractId contractId, String functionName, ContractFunctionParameters parameters, Hbar payableAmount) {

        ExecuteContractResult executeContractResult = contractClient.executeContract(
                contractId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                functionName,
                parameters,
                payableAmount);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertNotNull(executeContractResult.contractFunctionResult());

        return executeContractResult;
    }

    private enum ContractExecutionStage {
        CREATION,
        CALL
    }
}
