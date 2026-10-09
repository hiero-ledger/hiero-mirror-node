// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.client.AccountClient.ZERO_DELEGATION_ADDRESS;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.PURE;
import static org.hiero.mirror.test.e2e.acceptance.steps.CodeDelegationFeature.ContractMethods.PURE_MULTIPLY;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.web3j.crypto.transaction.type.TransactionType.EIP7702;

import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ContractResponse;
import org.hiero.mirror.rest.model.ContractResult;
import org.hiero.mirror.rest.model.PrestateAccountTrace;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.EthereumClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.config.FeatureProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.util.ContractCallResponseWrapper;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.web3j.crypto.AuthorizationTuple;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
public class CodeDelegationFeature extends AbstractFeature {

    private static final String CODE_DELEGATIONS_TAG = "@codeDelegations";

    private final AccountClient accountClient;
    private final EthereumClient ethereumClient;
    private final FeatureProperties featureProperties;
    private final MirrorNodeClient mirrorClient;

    private ExpandedAccountId account;
    private ContractCallResponseWrapper contractCallResponse;
    private DeployedContract delegatedContract;
    private String expectedDelegationAddress;
    private AuthorizationTuple authorization;

    @Before
    public void before(final Scenario scenario) {
        if (!scenario.getSourceTagNames().contains(CODE_DELEGATIONS_TAG)) {
            return;
        }

        assumeTrue(featureProperties.isCodeDelegationsEnabled(), "HIP-1340 code delegations are not enabled");
    }

    @Given("I successfully create a contract for code delegation")
    public void createContractForCodeDelegation() {
        delegatedContract = getContract(ESTIMATE_GAS);
        expectedDelegationAddress =
                toDelegationAddress(delegatedContract.contractId().toEvmAddress());
        assertThat(delegatedContract.contractId()).isNotNull();
    }

    @When("I create an account with code delegation to the contract")
    public void createAccountWithCodeDelegation() {
        var created =
                accountClient.createNewAccountWithDelegation(Numeric.hexStringToByteArray(expectedDelegationAddress));
        account = created.account();
        networkTransactionResponse = created.response();
        assertThat(account).isNotNull();
        assertThat(account.getAccountId()).isNotNull();
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
    }

    @When("I execute pureMultiply on the authority using an EIP-7702 ethereum transaction")
    public void executeDelegatedAccountViaEip7702() {
        var accountInfo = mirrorClient.getAccountDetailsByAccountId(account.getAccountId());
        var authorityNonce = accountInfo.getEthereumNonce() == null ? 0L : accountInfo.getEthereumNonce();
        // Self-sponsored EIP-7702: the transaction increments the sender nonce before
        // authorizations are applied, so the authorization must use nonce + 1.
        var authorizationNonce = authorityNonce + 1;
        authorization = ethereumClient.createAuthorization(
                account, delegatedContract.contractId().toEvmAddress(), authorizationNonce);
        var result = ethereumClient.executeContract(
                account.getPrivateKey(),
                evmAddress(account),
                PURE_MULTIPLY.getSelector(),
                null,
                EIP7702,
                List.of(authorization));
        networkTransactionResponse = result.networkTransactionResponse();
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
        assertThat(result.contractFunctionResult())
                .isNotNull()
                .satisfies(functionResult -> assertThat(functionResult.asBytes())
                        .as("EIP-7702 call returned no output; the authorization was likely ignored")
                        .hasSizeGreaterThanOrEqualTo(32));
        assertThat(result.contractFunctionResult().getUint256(0)).isEqualTo(BigInteger.valueOf(4));
    }

    @RetryAsserts
    @Then("the mirror node REST API should return status {int} for the EIP-7702 ethereum transaction")
    public void verifyEip7702Transaction(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(account.getAccountId().toString());
    }

    @RetryAsserts
    @Then("the mirror node REST API should return the authorization list for the EIP-7702 transaction")
    public void verifyAuthorizationList() {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var contractResult = mirrorClient.getContractResultByTransactionId(transactionId);
        assertThat(contractResult.getType()).isEqualTo(EIP7702.getRlpType().intValue());
        assertThat(contractResult.getAuthorizationList()).hasSize(1);
        var restAuthorization = contractResult.getAuthorizationList().getFirst();
        assertThat(restAuthorization.getAddress())
                .isEqualToIgnoringCase(toDelegationAddress(authorization.getAddress()));
        assertQuantityHexEquals(restAuthorization.getChainId(), authorization.getChainId());
        assertThat(restAuthorization.getNonce())
                .isEqualTo(authorization.getNonce().longValueExact());
        assertQuantityHexEquals(restAuthorization.getyParity(), authorization.getYParity());
        assertThat(restAuthorization.getR()).isEqualToIgnoringCase(toPaddedHex(authorization.getR()));
        assertThat(restAuthorization.getS()).isEqualToIgnoringCase(toPaddedHex(authorization.getS()));
    }

    @RetryAsserts
    @Then("the mirror node web3 API should return the prestate for the EIP-7702 transaction")
    public void verifyEip7702Prestate() {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var contractResult = mirrorClient.getContractResultByTransactionId(transactionId);
        assertThat(contractResult.getAuthorizationList()).isNotEmpty();
        var transactionHash = contractResult.getHash();

        try {
            final var fullResponse = mirrorClient.getContractPrestate(transactionHash, false, true, true);
            verifyDelegatedContractTraces(fullResponse, contractResult);
        } catch (HttpServerErrorException.NotImplemented notImplemented) {
            assertThat(notImplemented.getResponseBodyAsString()).contains("Not Implemented");
        }
    }

    @When("I clear the code delegation on the account")
    public void clearAccountCodeDelegation() {
        networkTransactionResponse = accountClient.setAccountDelegationAddress(account, ZERO_DELEGATION_ADDRESS);
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
    }

    @RetryAsserts
    @Then("the mirror node REST API should return the delegation address for the account")
    public void verifyDelegationAddress() {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());
        }

        var accountInfo = mirrorClient.getAccountDetailsByAccountId(account.getAccountId());
        assertThat(accountInfo.getDelegationAddress()).isEqualToIgnoringCase(expectedDelegationAddress);
    }

    @RetryAsserts
    @Then("the mirror node REST API should return an empty delegation address for the account")
    public void verifyEmptyDelegationAddress() {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var accountInfo = mirrorClient.getAccountDetailsByAccountId(account.getAccountId());
        assertThat(accountInfo.getDelegationAddress()).isEqualTo(HEX_PREFIX);
    }

    @When("I call pureMultiply on the delegated account via the mirror node REST API")
    public void callDelegatedAccountViaMirrorNode() {
        var contractCallRequest = ModelBuilder.contractCallRequest()
                .data(encodeData(ESTIMATE_GAS, PURE_MULTIPLY))
                .from(contractClient.getClientAddress())
                .to(evmAddress(account));
        contractCallResponse = callContract(contractCallRequest);
        assertThat(contractCallResponse).isNotNull();
    }

    @Then("the contract call result should equal {long}")
    public void verifyContractCallResult(long expected) {
        assertThat(contractCallResponse.getResultAsNumber()).isEqualTo(BigInteger.valueOf(expected));
    }

    @Then("the contract call result should be empty")
    public void verifyEmptyContractCallResult() {
        assertThat(contractCallResponse.getResult()).isEqualTo(HEX_PREFIX);
    }

    @When("I execute pureMultiply on the delegated account via a contract call transaction")
    public void executeDelegatedAccountViaContractCall() {
        var gas = featureProperties.getMaxContractFunctionGas();
        var accountContractId = ContractId.fromString(account.getAccountId().toString());
        var result = contractClient.executeContract(
                accountContractId, gas, PURE_MULTIPLY.getSelector(), (ContractFunctionParameters) null, null);
        networkTransactionResponse = result.networkTransactionResponse();
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
        assertThat(result.contractFunctionResult().getUint256(0)).isEqualTo(BigInteger.valueOf(4));
    }

    @RetryAsserts
    @Then("the mirror node REST API should return status {int} for the code delegation contract call")
    public void verifyCodeDelegationContractCallTransaction(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(account.getAccountId().toString());
    }

    @RetryAsserts
    @Then("the mirror node REST API should return the contract result for the delegated account")
    public void verifyContractResultForDelegatedAccount() {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var contractResult = mirrorClient.getContractResultByTransactionId(transactionId);
        verifyDelegatedCallResult(contractResult);

        var resultsByAccount = mirrorClient
                .getContractResultsById(account.getAccountId().toString())
                .getResults();
        assertThat(resultsByAccount).isNotEmpty().anySatisfy(this::verifyDelegatedCallResult);
    }

    private void verifyDelegatedContractTraces(final PrestateResponse response, final ContractResult contractResult) {
        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotEmpty();
        assertThat(response.getPost()).isNullOrEmpty();

        final var contractResultNonce = contractResult.getNonce();
        final var contractId = delegatedContract.contractId().toString();
        final var contractInfo = mirrorClient.getContractInfo(contractId);
        final var runtimeBytecode = contractInfo.getRuntimeBytecode();

        for (final var trace : response.getPre()) {
            if (isDelegatedContract(trace, contractInfo)) {
                assertThat(trace.getCode()).isEqualToIgnoringCase(runtimeBytecode);
                assertThat(trace.getNonce()).isEqualTo(contractResultNonce);
                verifyContractStorage(trace, contractId, "lt:" + contractResult.getTimestamp());
            }
        }
    }

    private boolean isDelegatedContract(final PrestateAccountTrace trace, final ContractResponse contractInfo) {
        return expectedDelegationAddress.equalsIgnoreCase(trace.getAddress())
                || (contractInfo.getEvmAddress() != null
                        && contractInfo.getEvmAddress().equalsIgnoreCase(trace.getAddress()));
    }

    private void verifyContractStorage(
            final PrestateAccountTrace trace, final String contractId, final String stateTimestamp) {
        final var storage = trace.getStorage();
        if (storage == null || storage.isEmpty()) {
            return;
        }

        final var restStorage = new HashMap<BigInteger, BigInteger>();
        final var states = mirrorClient
                .getContractStatesById(contractId, 100, stateTimestamp)
                .getState();
        if (states != null) {
            for (final var state : states) {
                restStorage.put(toWord(state.getSlot()), toWord(state.getValue()));
            }
        }

        // A slot cleared to zero has no value in the REST state, so a missing slot is treated as zero.
        assertThat(storage)
                .allSatisfy((slot, value) -> assertThat(restStorage.getOrDefault(toWord(slot), BigInteger.ZERO))
                        .as("storage slot %s at %s", slot, stateTimestamp)
                        .isEqualTo(toWord(value)));
    }

    private void verifyDelegatedCallResult(ContractResult contractResult) {
        assertThat(contractResult.getContractId())
                .isEqualTo(account.getAccountId().toString());
        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(List.of(
                        evmAddress(account),
                        toDelegationAddress(account.getAccountId().toEvmAddress())))
                .anySatisfy(expected -> assertThat(contractResult.getTo()).isEqualToIgnoringCase(expected));
        assertThat(Numeric.toBigInt(contractResult.getCallResult())).isEqualTo(BigInteger.valueOf(4));
    }

    private static String evmAddress(ExpandedAccountId accountId) {
        var publicKey = accountId.getPublicKey();
        if (publicKey != null && publicKey.isECDSA()) {
            return toDelegationAddress(publicKey.toEvmAddress().toString());
        }
        return toDelegationAddress(accountId.getAccountId().toEvmAddress());
    }

    private static String toDelegationAddress(String evmAddress) {
        var hex = evmAddress.startsWith(HEX_PREFIX) ? evmAddress : HEX_PREFIX + evmAddress;
        return hex.toLowerCase();
    }

    private static void assertQuantityHexEquals(String actual, BigInteger expected) {
        assertThat(Numeric.toBigInt(quantityOrZero(actual))).isEqualTo(expected);
    }

    private static String quantityOrZero(String hex) {
        return hex == null || hex.equals(HEX_PREFIX) ? HEX_PREFIX + "0" : hex;
    }

    private static BigInteger toWord(final String hex) {
        return Numeric.toBigInt(quantityOrZero(hex));
    }

    private static String toPaddedHex(BigInteger value) {
        return HEX_PREFIX + Numeric.toHexStringNoPrefixZeroPadded(value, 64);
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        PURE_MULTIPLY("pureMultiply");

        private final String selector;

        @Override
        public FunctionType getFunctionType() {
            return PURE;
        }
    }
}
