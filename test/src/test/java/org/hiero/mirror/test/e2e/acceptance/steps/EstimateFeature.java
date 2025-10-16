// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.MUTABLE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.PAYABLE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.PURE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.VIEW;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.ADDRESS_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_INVALID_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_TO_INVALID_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_TO_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_TO_INVALID_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_AND_CALL_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_AND_DESTROY;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_BYTECODE_DATA;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_2_OPCODE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_OPCODE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_NEW_INSTANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DESTROY;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_GAS_LEFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_MOCK_ADDRESS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_APPROVE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_ASSOCIATE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_DISSOCIATE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.INCREMENT_COUNTER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.LOGS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MESSAGE_SENDER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MESSAGE_SIGNER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MESSAGE_VALUE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MULTIPLY_NUMBERS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.NESTED_CALLS_LIMITED;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.NESTED_CALLS_POSITIVE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.REENTRANCY_CALL_ATTACK;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.STATE_UPDATE_OF_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.STATIC_CALL_TO_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.STATIC_CALL_TO_INVALID_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.TX_ORIGIN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.UPDATE_COUNTER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.UPDATE_TYPE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.WRONG_METHOD_SIGNATURE;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_TOKEN_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.extractTransactionId;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient;
import org.hiero.mirror.test.e2e.acceptance.config.Web3Properties;
import org.hiero.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;
import org.springframework.web.client.HttpClientErrorException;

@CustomLog
@RequiredArgsConstructor
public class EstimateFeature extends AbstractEstimateFeature {

    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final String RANDOM_ADDRESS =
            to32BytesString(RandomStringUtils.secure().next(40, HEX_DIGITS));
    private final TokenClient tokenClient;
    private final Web3Properties web3Properties;
    private final AccountClient accountClient;
    private DeployedContract deployedContract;
    private ContractId estimateGasContractId;
    private DeployedContract deployedPrecompileContract;
    private DeployedContract deployedERCContract;
    private String ercSolidityAddress;
    private ContractId ercContractId;

    private String contractSolidityAddress;
    private String mockAddress;
    byte[] addressSelector;
    private TokenId fungibleTokenId;
    private Address fungibleTokenAddress;
    private ExpandedAccountId receiverAccountId;
    private AccountId senderAccountId;

    @Given("I successfully create EstimateGas contract from contract bytes")
    public void createNewEstimateContract() {
        deployedContract = getContract(ESTIMATE_GAS);
        estimateGasContractId = deployedContract.contractId();
        contractSolidityAddress = deployedContract.contractId().toEvmAddress();
        receiverAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        senderAccountId =
                accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId();
    }

    @Given("I successfully create fungible token")
    public void createFungibleToken() {
        var tokenResponse = tokenClient.getToken(FUNGIBLE);
        fungibleTokenId = tokenResponse.tokenId();
        fungibleTokenAddress = asAddress(fungibleTokenId);
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Given("I successfully create Precompile contract from contract bytes")
    public void createNewPrecompileContract() {
        deployedPrecompileContract = getContract(PRECOMPILE);
    }

    @Given("I successfully create ERC contract from contract bytes")
    public void createNewERCContract() {
        deployedERCContract = getContract(ERC);
        ercSolidityAddress = deployedERCContract.contractId().toEvmAddress();
        ercContractId = deployedERCContract.contractId();
    }

    @Then("the mirror node REST API should return status {int} for the estimate contract creation")
    public void verifyMirrorAPIResponses(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @RetryAsserts
    @Given("I verify the estimate contract bytecode is deployed")
    public void verifyEstimateContractIsDeployed() {
        verifyContractDeployed(contractSolidityAddress);
    }

    private void verifyContractDeployed(String contractAddress) {
        var response = mirrorClient.getContractInfo(contractAddress);
        Assertions.assertThat(response.getBytecode()).isNotBlank();
        Assertions.assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    @And("lower deviation is {int}% and upper deviation is {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    @Then("I call estimateGas without arguments that multiplies two numbers")
    public void multiplyEstimateCall() throws ExecutionException, InterruptedException {

        validateGasEstimation(
                estimateGasContractId,
                MULTIPLY_NUMBERS.getSelector(),
                senderAccountId,
                MULTIPLY_NUMBERS.getActualGas());
    }

    @Then("I call estimateGas with function msgSender")
    public void msgSenderEstimateCall() throws ExecutionException, InterruptedException {

        validateGasEstimation(
                estimateGasContractId, MESSAGE_SENDER.getSelector(), senderAccountId, MESSAGE_SENDER.getActualGas());
    }

    @Then("I call estimateGas with function tx origin")
    public void txOriginEstimateCall() throws ExecutionException, InterruptedException {

        validateGasEstimation(
                estimateGasContractId, TX_ORIGIN.getSelector(), senderAccountId, TX_ORIGIN.getActualGas());
    }

    @Then("I call estimateGas with function messageValue")
    public void msgValueEstimateCall() throws ExecutionException, InterruptedException {

        validateGasEstimation(
                estimateGasContractId, MESSAGE_VALUE.getSelector(), senderAccountId, MESSAGE_VALUE.getActualGas());
    }

    @Then("I call estimateGas with function messageSigner")
    public void msgSignerEstimateCall() throws ExecutionException, InterruptedException {

        validateGasEstimation(
                estimateGasContractId, MESSAGE_SIGNER.getSelector(), senderAccountId, MESSAGE_SIGNER.getActualGas());
    }

    @RetryAsserts
    @Then("I call estimateGas with function balance of address")
    public void addressBalanceEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters().addAddress(contractSolidityAddress);

        validateGasEstimation(estimateGasContractId, ADDRESS_BALANCE, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that changes contract slot information"
            + " by updating global contract field with the passed argument")
    public void updateCounterEstimateCall() throws ExecutionException, InterruptedException {
        /*
         * NB: Variations in gas costs for contract storage slot updates in the EVM:
         *
         * 1. Same Value Update:
         *    - Minimal gas consumption (e.g., 27726) due to no effective operation.
         *
         * 2. Different Non-Zero Value Update:
         *    - Increased gas usage (e.g., 30946) for actual computational changes.
         *
         * 3. Zero to Non-Zero Update:
         *    - Highest gas cost (e.g., 50611) because initializing a slot is more complex.
         *
         * 4. Non-Zero to Zero Update
         *    - Distinct gas cost (e.g, 36452) due to process of clearing the storage slot.
         */
        final var parameters = new ContractFunctionParameters()
                .addAddress(contractSolidityAddress)
                .addUint256(new BigInteger("100"));

        validateGasEstimation(estimateGasContractId, UPDATE_COUNTER, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE op code")
    public void deployContractViaCreateOpcodeEstimateCall() throws ExecutionException, InterruptedException {
        var data = encodeData(ESTIMATE_GAS, DEPLOY_CONTRACT_VIA_CREATE_OPCODE);
        validateGasEstimation(
                estimateGasContractId,
                DEPLOY_CONTRACT_VIA_CREATE_OPCODE.getSelector(),
                senderAccountId,
                DEPLOY_CONTRACT_VIA_CREATE_OPCODE.getActualGas());
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE2 op code")
    public void deployContractViaCreateTwoOpcodeEstimateCall() throws ExecutionException, InterruptedException {
        validateGasEstimation(
                estimateGasContractId,
                DEPLOY_CONTRACT_VIA_CREATE_2_OPCODE.getSelector(),
                senderAccountId,
                DEPLOY_CONTRACT_VIA_CREATE_2_OPCODE.getActualGas());
    }

    @Then("I get mock contract address and getAddress selector")
    public void getMockAddress() {
        var data = encodeData(ESTIMATE_GAS, GET_MOCK_ADDRESS);
        mockAddress = callContract(data, contractSolidityAddress).getResultAsAddress();
        addressSelector = new BigInteger("0x38cc4831".substring(2), 16).toByteArray();
    }

    @Then("I call estimateGas with function that makes a static call to a method from a different contract")
    public void staticCallToContractEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(mockAddress).toString())
                .addBytes4(addressSelector);

        validateGasEstimation(estimateGasContractId, STATIC_CALL_TO_CONTRACT, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes a delegate call to a method from a different contract")
    public void delegateCallToContractEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(mockAddress).toString())
                .addBytes4(addressSelector);

        validateGasEstimation(estimateGasContractId, DELEGATE_CALL_TO_CONTRACT, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes a call code to a method from a different contract")
    public void callCodeToContractEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(mockAddress).toString())
                .addBytes4(addressSelector);

        validateGasEstimation(estimateGasContractId, CALL_CODE_TO_CONTRACT, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that performs LOG0, LOG1, LOG2, LOG3, LOG4 operations")
    public void logsEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters().addAddress(contractSolidityAddress);

        validateGasEstimation(estimateGasContractId, LOGS, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that performs self destruct")
    public void destroyEstimateCall() throws ExecutionException, InterruptedException {
        validateGasEstimation(estimateGasContractId, DESTROY.getSelector(), senderAccountId, DESTROY.getActualGas());
    }

    @Then("I call estimateGas with request body that contains wrong method signature")
    public void wrongMethodSignatureEstimateCall() {
        var mockContractId = ContractId.fromEvmAddress(0, 0, mockAddress);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryWithoutParams(
                        mockContractId,
                        WRONG_METHOD_SIGNATURE.getSelector(),
                        senderAccountId,
                        WRONG_METHOD_SIGNATURE.getActualGas()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with wrong encoded parameter")
    public void wrongEncodedParameterEstimateCall() {
        // wrong encoded address -> it should contain leading zero's equal to 64 characters
        var wrongEncodedAddress = "5642";
        // 3ec4de35 is the address balance signature, we cant send wrong encoded parameter with headlong
        assertContractCallReturnsBadRequest("3ec4de35" + wrongEncodedAddress, contractSolidityAddress);
    }

    // Cannot send request without from with modularized EVM
    @Then("I call estimateGas with non-existing from address in the request body")
    public void wrongFromParameterEstimateCall() throws ExecutionException, InterruptedException {
        if (!web3Properties.isModularizedServices()) {
            var estimateGasResult = mirrorClient.estimateGasQueryWithoutParams(
                    estimateGasContractId,
                    MESSAGE_SIGNER.getSelector(),
                    senderAccountId,
                    MESSAGE_SIGNER.getActualGas());

            assertWithinDeviation(
                    MESSAGE_SIGNER.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);
        }
    }

    @Then("I call estimateGas with function that makes a call to invalid smart contract")
    public void callToInvalidSmartContractEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(RANDOM_ADDRESS).toString());

        validateGasEstimation(estimateGasContractId, CALL_TO_INVALID_CONTRACT, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes a delegate call to invalid smart contract")
    public void delegateCallToInvalidSmartContractEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(RANDOM_ADDRESS).toString());

        validateGasEstimation(estimateGasContractId, DELEGATE_CALL_TO_INVALID_CONTRACT, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes a static call to invalid smart contract")
    public void staticCallToInvalidSmartContractEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(RANDOM_ADDRESS).toString());

        validateGasEstimation(estimateGasContractId, STATIC_CALL_TO_INVALID_CONTRACT, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes a call code to invalid smart contract")
    public void callCodeToInvalidSmartContractEstimateCall() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(RANDOM_ADDRESS).toString());

        validateGasEstimation(estimateGasContractId, CALL_CODE_TO_INVALID_CONTRACT, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes call to an external contract function")
    public void callCodeToExternalContractFunction() throws ExecutionException, InterruptedException {
        final var parameters =
                new ContractFunctionParameters().addUint256(BigInteger.TWO).addAddress(contractSolidityAddress);

        validateGasEstimation(
                estimateGasContractId, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes delegate call to an external contract function")
    public void delegateCallCodeToExternalContractFunction() throws ExecutionException, InterruptedException {
        final var parameters =
                new ContractFunctionParameters().addUint256(new BigInteger("3")).addAddress(contractSolidityAddress);

        validateGasEstimation(
                estimateGasContractId, DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes call to an external contract view function")
    public void callCodeToExternalContractViewFunction() throws ExecutionException, InterruptedException {
        final var parameters =
                new ContractFunctionParameters().addUint256(BigInteger.ONE).addAddress(contractSolidityAddress);

        validateGasEstimation(
                estimateGasContractId, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that makes a state update to a contract")
    public void stateUpdateContractFunction() throws ExecutionException, InterruptedException {
        // making 5 times to state update
        final var parameters = new ContractFunctionParameters().addUint256(new BigInteger("5"));

        validateGasEstimation(estimateGasContractId, STATE_UPDATE_OF_CONTRACT, parameters, senderAccountId);
    }

    @Then(
            "I call estimateGas with function that makes a state update to a contract several times and estimateGas is higher")
    public void progressiveStateUpdateContractFunction() throws ExecutionException, InterruptedException {
        // making 5 times to state update
        final var firstParams = new ContractFunctionParameters().addUint256(new BigInteger("5"));
        var firstResponse = mirrorClient.estimateGasQueryTopLevelCall(
                estimateGasContractId, STATE_UPDATE_OF_CONTRACT, firstParams, senderAccountId, Optional.empty());
        // making 10 times to state update
        final var secondParams = new ContractFunctionParameters().addUint256(BigInteger.TEN);
        var secondResponse = mirrorClient.estimateGasQueryTopLevelCall(
                estimateGasContractId, STATE_UPDATE_OF_CONTRACT, secondParams, senderAccountId, Optional.empty());

        assertTrue(secondResponse > firstResponse);
    }

    @Then("I call estimateGas with function that executes reentrancy attack with call")
    public void reentrancyCallAttackFunction() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(RANDOM_ADDRESS).toString())
                .addUint256(new BigInteger("10000000000"));

        validateGasEstimation(estimateGasContractId, REENTRANCY_CALL_ATTACK, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that executes gasLeft")
    public void getGasLeftContractFunction() throws ExecutionException, InterruptedException {
        validateGasEstimation(
                estimateGasContractId, GET_GAS_LEFT.getSelector(), senderAccountId, GET_GAS_LEFT.getActualGas());
    }

    @Then("I call estimateGas with function that executes positive nested calls")
    public void positiveNestedCallsFunction() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addUint256(BigInteger.ONE)
                .addUint256(BigInteger.TEN)
                .addAddress(contractSolidityAddress);

        validateGasEstimation(estimateGasContractId, NESTED_CALLS_POSITIVE, parameters, senderAccountId);
    }

    @Then("I call estimateGas with function that executes limited nested calls")
    public void limitedNestedCallsFunction() throws ExecutionException, InterruptedException {
        // verify that after exceeding a number of nested calls that the estimated gas would return the same
        // we will execute with 500, 1024 and 1025, and it should return the same estimatedGas
        final var firstParameters = new ContractFunctionParameters()
                .addUint256(BigInteger.ONE)
                .addUint256(new BigInteger("500"))
                .addAddress(contractSolidityAddress);
        var estimateGasResult = mirrorClient.estimateGasQueryNestedCall(
                estimateGasContractId,
                NESTED_CALLS_LIMITED.getSelector(),
                firstParameters,
                senderAccountId,
                NESTED_CALLS_LIMITED.getActualGas());
        assertWithinDeviation(
                NESTED_CALLS_LIMITED.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        final var secondParameters = new ContractFunctionParameters()
                .addUint256(BigInteger.ONE)
                .addUint256(new BigInteger("1024"))
                .addAddress(contractSolidityAddress);

        final var secondEstimateGasResult = mirrorClient.estimateGasQueryNestedCall(
                estimateGasContractId,
                NESTED_CALLS_LIMITED.getSelector(),
                secondParameters,
                senderAccountId,
                NESTED_CALLS_LIMITED.getActualGas());
        assertWithinDeviation(
                NESTED_CALLS_LIMITED.getActualGas(), (int) secondEstimateGasResult, lowerDeviation, upperDeviation);

        final var thirdParameters = new ContractFunctionParameters()
                .addUint256(BigInteger.ONE)
                .addUint256(new BigInteger("1025"))
                .addAddress(contractSolidityAddress);

        final var thirdEstimateGasResult = mirrorClient.estimateGasQueryNestedCall(
                estimateGasContractId,
                NESTED_CALLS_LIMITED.getSelector(),
                thirdParameters,
                senderAccountId,
                NESTED_CALLS_LIMITED.getActualGas());
        assertWithinDeviation(
                NESTED_CALLS_LIMITED.getActualGas(), (int) thirdEstimateGasResult, lowerDeviation, upperDeviation);
    }

    @And("I approve the contract to use FT")
    public void approveFungibleWithERCContract() {
        final var ercTestContractId = TestUtil.fromSolidityAddress(ercSolidityAddress);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, ercTestContractId, 10);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @And("I associate the receiver account with the FT")
    public void associateReceiverWithFungibleEstimateGas() {
        // associating the token with the token address
        networkTransactionResponse = tokenClient.associate(receiverAccountId, fungibleTokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I associate ERC contract with the FT")
    public void associateTokensWithContract() {
        // In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedERCContract.contractId(), fungibleTokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @And("I approve the receiver account to use FT and transfer FT to the erc contract")
    public void approveAndTransferFungibleToken() {
        accountClient.approveToken(fungibleTokenId, receiverAccountId.getAccountId(), 50L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                AccountId.fromString(deployedERCContract.contractId().toString()),
                null,
                10);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    // Disabled because currently the ERC contracts cannot interact with long zero addresses. Will be fixed in future
    // from services team
    @Then("I call estimateGas with IERC20 token transfer using long zero address as receiver")
    public void ierc20TransferWithLongZeroAddressForReceiver() throws ExecutionException, InterruptedException {
        if (!web3Properties.isModularizedServices()) {
            final var parameters = new ContractFunctionParameters()
                    .addAddress(fungibleTokenAddress.toString())
                    .addAddress(asAddress(receiverAccountId).toString())
                    .addUint256(new BigInteger("5"));

            validateGasEstimation(ercContractId, IERC20_TOKEN_TRANSFER, parameters, senderAccountId);
        }
    }

    @Then("I call estimateGas with IERC20 token transfer using evm address as receiver")
    public void ierc20TransferWithEvmAddressForReceiver() throws InterruptedException, ExecutionException {
        var accountInfo = mirrorClient.getAccountDetailsByAccountId(receiverAccountId.getAccountId());
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddress.toString())
                .addAddress(asAddress(accountInfo.getEvmAddress().replace(HEX_PREFIX, ""))
                        .toString())
                .addUint256(BigInteger.ONE);

        validateGasEstimation(ercContractId, IERC20_TOKEN_TRANSFER, parameters, senderAccountId);
    }

    @Then("I call estimateGas with IERC20 token approve using evm address as receiver")
    public void ierc20ApproveWithEvmAddressForReceiver() throws ExecutionException, InterruptedException {
        var accountInfo = mirrorClient.getAccountDetailsByAccountId(receiverAccountId.getAccountId());
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddress.toString())
                .addAddress(asAddress(accountInfo.getEvmAddress().replace(HEX_PREFIX, ""))
                        .toString())
                .addUint256(BigInteger.ONE);

        validateGasEstimation(ercContractId, IERC20_TOKEN_APPROVE, parameters, senderAccountId);
    }

    @Then("I call estimateGas with IERC20 token associate using evm address as receiver")
    public void ierc20AssociateWithEvmAddressForReceiver() {
        var contractCallRequest = ModelBuilder.contractCallRequest(IERC20_TOKEN_ASSOCIATE.getActualGas())
                .data(encodeData(IERC20_TOKEN_ASSOCIATE))
                .estimate(true)
                .to(fungibleTokenAddress.toString());

        var msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);

        var estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();

        assertWithinDeviation(IERC20_TOKEN_ASSOCIATE.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
    }

    @Then("I call estimateGas with IERC20 token dissociate using evm address as receiver")
    public void ierc20DissociateWithEvmAddressForReceiver() {
        var contractCallRequest = ModelBuilder.contractCallRequest(IERC20_TOKEN_DISSOCIATE.getActualGas())
                .data(encodeData(IERC20_TOKEN_DISSOCIATE))
                .estimate(true)
                .to(fungibleTokenAddress.toString());

        var msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);

        var estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();

        assertWithinDeviation(IERC20_TOKEN_DISSOCIATE.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
    }

    @Then("I call estimateGas with contract deploy with bytecode as data")
    public void contractDeployEstimateGas() {
        var bytecodeData = deployedContract.compiledSolidityArtifact().getBytecode();
        var contractCallRequest = ModelBuilder.contractCallRequest(DEPLOY_CONTRACT_VIA_BYTECODE_DATA.getActualGas())
                .data(bytecodeData)
                .estimate(true);

        var msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);
        var estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();

        assertWithinDeviation(
                DEPLOY_CONTRACT_VIA_BYTECODE_DATA.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
    }

    @Then("I call estimateGas with contract deploy with bytecode as data with sender")
    public void contractDeployEstimateGasWithSender() {
        var bytecodeData = deployedContract.compiledSolidityArtifact().getBytecode();

        var contractCallRequest = ModelBuilder.contractCallRequest(DEPLOY_CONTRACT_VIA_BYTECODE_DATA.getActualGas())
                .data(bytecodeData)
                .estimate(true)
                .from(contractClient.getClientAddress());

        var msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);
        var estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();

        assertWithinDeviation(
                DEPLOY_CONTRACT_VIA_BYTECODE_DATA.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
    }

    @Then("I call estimateGas with contract deploy with bytecode as data with invalid sender")
    public void contractDeployEstimateGasWithInvalidSender() {
        var bytecodeData = deployedContract.compiledSolidityArtifact().getBytecode();
        if (web3Properties.isModularizedServices()) {
            var contractCallRequest = ModelBuilder.contractCallRequest(DEPLOY_CONTRACT_VIA_BYTECODE_DATA.getActualGas())
                    .data(bytecodeData)
                    .estimate(true)
                    .from("0x0000000000000000000000000000000000000167");
            assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequest))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        } else {
            var contractCallRequest = ModelBuilder.contractCallRequest(DEPLOY_CONTRACT_VIA_BYTECODE_DATA.getActualGas())
                    .data(bytecodeData)
                    .estimate(true)
                    .from("0x0000000000000000000000000000000000000167");

            var msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);
            var estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                    .toBigInteger()
                    .intValue();

            assertWithinDeviation(
                    DEPLOY_CONTRACT_VIA_BYTECODE_DATA.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
        }
    }

    @Then("I execute contractCall for function that changes the contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumed() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, UPDATE_COUNTER, new BigInteger("5"));
        var txId = executeContractTransaction(deployedContract, UPDATE_COUNTER);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall with delegatecall and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaDelegateCall() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS, DELEGATE_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, DELEGATE_CALL_TO_CONTRACT);
        verifyGasConsumed(txId);
    }

    @Then(
            "I execute contractCall with delegatecall with low gas and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaDelegateCallWithLowGas() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS, DELEGATE_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, 21500L, DELEGATE_CALL_TO_CONTRACT);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall with callcode and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaCallCode() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS, CALL_CODE_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, DELEGATE_CALL_TO_CONTRACT);
        verifyGasConsumed(txId);
    }

    @Then(
            "I execute contractCall with callcode with low gas and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaCallCodeWithLowGas() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS, CALL_CODE_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, 21500L, DELEGATE_CALL_TO_CONTRACT);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall with static call and verify gasConsumed")
    public void getAddressViaStaticCall() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, GET_MOCK_ADDRESS);
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS, STATIC_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, STATIC_CALL_TO_CONTRACT);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall with static call with low gas and verify gasConsumed")
    public void getAddressViaStaticCallWithLowGas() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, GET_MOCK_ADDRESS);
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS, STATIC_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, 21500L, STATIC_CALL_TO_CONTRACT);
        verifyGasConsumed(txId);
    }

    @Then("I trigger fallback function with transfer and verify gasConsumed")
    public void triggerFallbackAndTransferFundsToContract() {
        gasConsumedSelector = encodeData(WRONG_METHOD_SIGNATURE).getBytes();
        var txId = executeContractTransaction(deployedContract, WRONG_METHOD_SIGNATURE, Hbar.fromTinybars(100));
        verifyGasConsumed(txId);
    }

    @Then("I trigger fallback function with send and verify gasConsumed")
    public void triggerFallbackAndSendFundsToContract() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, UPDATE_TYPE, BigInteger.TWO);
        executeContractTransaction(deployedContract, UPDATE_TYPE, null);

        gasConsumedSelector = encodeData(WRONG_METHOD_SIGNATURE).getBytes();
        var txId = executeContractTransaction(deployedContract, WRONG_METHOD_SIGNATURE, Hbar.fromTinybars(100));
        verifyGasConsumed(txId);
    }

    @Then("I trigger fallback function with call and verify gasConsumed")
    public void triggerFallWithCallToContract() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, UPDATE_TYPE, new BigInteger("3"));
        executeContractTransaction(deployedContract, UPDATE_TYPE, null);

        gasConsumedSelector = encodeData(WRONG_METHOD_SIGNATURE).getBytes();
        var txId = executeContractTransaction(deployedContract, WRONG_METHOD_SIGNATURE, Hbar.fromTinybars(100));
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall for nested function and verify gasConsumed")
    public void nestedCalls() {
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS,
                CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                BigInteger.TWO,
                asAddress(contractSolidityAddress));
        var txId = executeContractTransaction(deployedContract, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall for nested functions with lower gas limit and verify gasConsumed")
    public void manyNestedLowerGasLimitCalls() {
        gasConsumedSelector = encodeDataToByteArray(
                ESTIMATE_GAS,
                CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                new BigInteger("21"),
                asAddress(contractSolidityAddress));
        var txId = executeContractTransaction(deployedContract, 50000L, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall for failing nested functions and verify gasConsumed")
    public void failingNestedFunction() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, CALL_TO_INVALID_CONTRACT, asAddress(mockAddress));
        var txId = executeContractTransaction(deployedContract, CALL_TO_INVALID_CONTRACT);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall for failing precompile function and verify gasConsumed")
    public void failingPrecompileFunction() {
        gasConsumedSelector = encodeDataToByteArray(PRECOMPILE, IS_TOKEN_SELECTOR, fungibleTokenAddress);
        var txId = executeContractTransaction(deployedPrecompileContract, 21216L, IS_TOKEN_SELECTOR);
        verifyGasConsumed(txId);
    }

    @Then("I execute contractCall for contract deploy function via create and verify gasConsumed")
    public void deployContractViaCreate() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_CONTRACT_VIA_CREATE_OPCODE);
        var txId = executeContractTransaction(deployedContract, DEPLOY_CONTRACT_VIA_CREATE_OPCODE);
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        verifyGasConsumed(txId, contractId, true);
    }

    @Then("I execute contractCall for contract deploy function via create2 and verify gasConsumed")
    public void deployContractViaCreateTwo() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_CONTRACT_VIA_CREATE_2_OPCODE);
        var txId = executeContractTransaction(deployedContract, DEPLOY_CONTRACT_VIA_CREATE_2_OPCODE);
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        verifyGasConsumed(txId, contractId, true);
    }

    @Then("I execute contractCall failing to deploy contract due to low gas and verify gasConsumed")
    public void failDeployComplexContract() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_NEW_INSTANCE);
        var txId = executeContractTransaction(deployedContract, 40000L, DEPLOY_NEW_INSTANCE);
        verifyGasConsumed(txId);
    }

    @Then("I execute deploy and call contract and verify gasConsumed")
    public void deployAndCallContract() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_AND_CALL_CONTRACT, new BigInteger("5"));
        var txId = executeContractTransaction(deployedContract, DEPLOY_AND_CALL_CONTRACT);
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        verifyGasConsumed(txId, contractId, true);
    }

    @Then("I execute deploy and call contract that fails and verify gasConsumed")
    public void deployAndCallFailContract() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_AND_CALL_CONTRACT, new BigInteger("11"));
        var txId = executeContractTransaction(deployedContract, DEPLOY_AND_CALL_CONTRACT);
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        verifyGasConsumed(txId, contractId, true);
    }

    @Then("I execute deploy and selfdestruct and verify gasConsumed")
    public void deployAndSelfDestructContract() {
        gasConsumedSelector = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_AND_DESTROY);
        var txId = executeContractTransaction(deployedContract, DEPLOY_AND_DESTROY);
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        verifyGasConsumed(txId, contractId, true);
    }

    @Then("I execute create operation with bad contract and verify gasConsumed")
    public void deployBadContract() {
        var contractPath = "classpath:solidity/artifacts/contracts/EstimateGasContract.sol/DummyContract.json";
        var txId = createContractAndReturnTransactionId(contractPath);
        var contractId = Objects.requireNonNull(
                        mirrorClient.getTransactions(txId).getTransactions())
                .getFirst()
                .getEntityId();
        gasConsumedSelector = Objects.requireNonNull(
                mirrorClient.getContractResultByTransactionId(txId).getFailedInitcode());
        verifyGasConsumed(txId, contractId, false);
    }

    @Then("I execute create operation with complex contract and verify gasConsumed")
    public void deployEstimateContract() {
        var contractPath = ESTIMATE_GAS.getPath();
        var txId = createContractAndReturnTransactionId(contractPath);
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        gasConsumedSelector =
                Objects.requireNonNull(mirrorClient.getContractInfo(contractId).getBytecode());
        verifyGasConsumed(txId, contractId, false);
    }

    @Then("I execute create operation with complex contract and lower gas limit and verify gasConsumed")
    public void deployEstimateContractWithLowGas() {
        var contractPath = ESTIMATE_GAS.getPath();
        var txId = createContractAndReturnTransactionId(contractPath, 2150000L);
        var contractId = networkTransactionResponse.getReceipt().contractId.toEvmAddress();
        gasConsumedSelector =
                Objects.requireNonNull(mirrorClient.getContractInfo(contractId).getBytecode());
        verifyGasConsumed(txId, contractId, false);
    }

    private String executeContractTransaction(DeployedContract deployedContract, SelectorInterface contractMethods) {

        return executeContractTransaction(
                deployedContract,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                contractMethods,
                (byte[]) gasConsumedSelector,
                null);
    }

    private String executeContractTransaction(
            DeployedContract deployedContract, SelectorInterface contractMethods, Hbar amount) {

        return executeContractTransaction(
                deployedContract,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                contractMethods,
                (byte[]) gasConsumedSelector,
                amount);
    }

    private String executeContractTransaction(
            DeployedContract deployedContract, Long gas, SelectorInterface contractMethods) {

        return executeContractTransaction(deployedContract, gas, contractMethods, (byte[]) gasConsumedSelector, null);
    }

    private String executeContractTransaction(
            DeployedContract deployedContract,
            Long gas,
            SelectorInterface contractMethods,
            byte[] parameters,
            Hbar amount) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(), gas, contractMethods.getSelector(), parameters, amount);
            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return networkTransactionResponse.getTransactionIdStringNoCheckSum();
        } catch (Exception e) {
            return extractTransactionId(e.getMessage());
        }
    }

    private String createContractAndReturnTransactionId(String resourcePath, Long gas) {
        var resource = resourceLoader.getResource(resourcePath);
        try (var in = resource.getInputStream()) {
            CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
            var fileId =
                    persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst(HEX_PREFIX, ""));
            try {
                networkTransactionResponse =
                        contractClient.createContract(fileId, gas, Hbar.fromTinybars(6_000_000), null);
                return networkTransactionResponse.getTransactionIdStringNoCheckSum();
            } catch (Exception e) {
                return extractTransactionId(e.getMessage());
            }
        } catch (IOException e) {
            log.warn("Exception: ", e);
            throw new RuntimeException(e);
        }
    }

    private String createContractAndReturnTransactionId(String resourcePath) {
        return createContractAndReturnTransactionId(
                resourcePath,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas());
    }

    /**
     * Estimate gas values are hardcoded at this moment until we get better solution such as actual gas used returned
     * from the consensus node. It will be changed in future PR when actualGasUsed field is added to the protobufs.
     */
    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements ContractMethodInterface {
        ADDRESS_BALANCE("addressBalance", 21735, VIEW),
        CALL_CODE_TO_CONTRACT("callCodeToContract", 25128, MUTABLE),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("callExternalFunctionNTimes", 25168, MUTABLE),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW("delegatecallExternalViewFunctionNTimes", 29809, MUTABLE),
        CALL_CODE_TO_INVALID_CONTRACT("callCodeToInvalidContract", 24486, MUTABLE),
        CALL_TO_INVALID_CONTRACT("callToInvalidContract", 24807, MUTABLE),
        DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("delegatecallExternalFunctionNTimes", 29334, MUTABLE),
        DELEGATE_CALL_TO_CONTRACT("delegateCallToContract", 25124, MUTABLE),
        DELEGATE_CALL_TO_INVALID_CONTRACT("delegateCallToInvalidContract", 24803, MUTABLE),
        DEPLOY_CONTRACT_VIA_CREATE_OPCODE("deployViaCreate", 140631, MUTABLE),
        DEPLOY_CONTRACT_VIA_CREATE_2_OPCODE("deployViaCreate2", 140631, MUTABLE),
        DEPLOY_CONTRACT_VIA_BYTECODE_DATA("", 2133605, MUTABLE),
        DEPLOY_NEW_INSTANCE(
                "createClone", 0, MUTABLE), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        DEPLOY_AND_CALL_CONTRACT("deployAndCallMockContract", 0, MUTABLE),
        DEPLOY_AND_DESTROY(
                "deployDestroy", 0, MUTABLE), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        DESTROY("destroy", 26300, MUTABLE),
        GET_GAS_LEFT("getGasLeft", 21313, VIEW),
        GET_MOCK_ADDRESS("getMockContractAddress", 0, VIEW),
        INCREMENT_COUNTER(
                "incrementCounter", 0, MUTABLE), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        LOGS("logs", 28822, MUTABLE),
        MESSAGE_SENDER("msgSender", 21365, VIEW),
        MESSAGE_SIGNER("msgSig", 21361, PURE),
        MESSAGE_VALUE("msgValue", 21265, PAYABLE),
        MULTIPLY_NUMBERS("pureMultiply", 21281, PURE),
        NESTED_CALLS_LIMITED("nestedCalls", 175607, MUTABLE),
        NESTED_CALLS_POSITIVE("nestedCalls", 35975, MUTABLE),
        REENTRANCY_CALL_ATTACK("reentrancyWithCall", 56426, MUTABLE),
        STATIC_CALL_TO_CONTRACT("staticCallToContract", 25146, VIEW),
        STATIC_CALL_TO_INVALID_CONTRACT("staticCallToInvalidContract", 24826, VIEW),
        STATE_UPDATE_OF_CONTRACT("updateStateNTimes", 29000, MUTABLE),
        TX_ORIGIN("txOrigin", 21342, VIEW),
        UPDATE_COUNTER("updateCounter", 26538, MUTABLE),
        UPDATE_TYPE("updateType", 0, MUTABLE), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        WRONG_METHOD_SIGNATURE("ffffffff()", 0, MUTABLE),
        IERC20_TOKEN_TRANSFER("transfer", 43337, MUTABLE),
        IERC20_TOKEN_APPROVE("approve", 728550, MUTABLE),
        IERC20_TOKEN_ASSOCIATE("associate()", 728033, MUTABLE),
        IERC20_TOKEN_DISSOCIATE("dissociate()", 728033, MUTABLE),
        CREATE_CHILD("createChild", 0, MUTABLE),
        GET_BYTE_CODE("getBytecode", 0, PURE);

        private final String selector;
        private final int actualGas;
        private final FunctionType functionType;
    }
}
