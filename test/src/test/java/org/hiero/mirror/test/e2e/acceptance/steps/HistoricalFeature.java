// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static org.hiero.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.ALLOWANCE_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.GET_APPROVED_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.GET_OWNER_OF_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.ADDRESS_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ALLOWANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BALANCE_OF;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_APPROVED;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_FUNGIBLE_TOKEN_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_NON_FUNGIBLE_TOKEN_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_EXPIRY_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_KEY;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_TYPE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_APPROVED_FOR_ALL;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UPDATE_TOKEN_KEYS;
import static org.hiero.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_COUNTER;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.ALLOWANCE_DIRECT_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.BALANCE_OF_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_APPROVED_DIRECT_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_TOKEN_DEFAULT_FREEZE_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_TOKEN_DEFAULT_KYC_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_KYC_GRANTED_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_TOKEN_FROZEN_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asAddress;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.CustomRoyaltyFee;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.props.Order;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.hiero.mirror.test.e2e.acceptance.util.ContractCallResponseWrapper;

@CustomLog
@RequiredArgsConstructor
public class HistoricalFeature extends AbstractEstimateFeature {
    private static final long CUSTOM_FIXED_FEE_AMOUNT = 10L;

    private final AccountClient accountClient;
    private final TokenClient tokenClient;
    private DeployedContract deployedEstimateContract;
    private DeployedContract deployedEstimatePrecompileContract;
    private DeployedContract deployedPrecompileContract;
    private DeployedContract deployedErcContract;
    private String estimateContractSolidityAddress;
    private String estimatePrecompileContractSolidityAddress;
    private String precompileContractSolidityAddress;
    private String ercContractSolidityAddress;
    private ExpandedAccountId receiverAccountId;
    private ExpandedAccountId secondReceiverAccountId;
    private ExpandedAccountId deletableAccountId;
    private ExpandedAccountId admin;
    private Address receiverAccountAddress;
    private Address adminAddress;

    @Given("I successfully create estimateGas contract")
    public void createNewEstimateContract() {
        deployedEstimateContract = getContract(ESTIMATE_GAS);
        estimateContractSolidityAddress = deployedEstimateContract.contractId().toEvmAddress();
    }

    @Given("I successfully create estimate precompile contract")
    public void createNewEstimatePrecompileContract() {
        deployedEstimatePrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        estimatePrecompileContractSolidityAddress =
                deployedEstimatePrecompileContract.contractId().toEvmAddress();
    }

    @Given("I successfully create erc contract")
    public void createNewErcContract() {
        deployedErcContract = getContract(ERC);
        ercContractSolidityAddress = deployedErcContract.contractId().toEvmAddress();
    }

    @Given("I successfully create precompile contract")
    public void createNewPrecompileContract() {
        deployedPrecompileContract = getContract(PRECOMPILE);
        precompileContractSolidityAddress =
                deployedPrecompileContract.contractId().toEvmAddress();
    }

    @Given("I create admin and receiver accounts")
    public void createAccounts() {
        receiverAccountId = accountClient.getAccount(AccountNameEnum.DAVE);
        secondReceiverAccountId = accountClient.getAccount(AccountNameEnum.BOB);
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccountAddress = asAddress(receiverAccountId);
        adminAddress = asAddress(admin);
    }

    @Then("the mirror node REST API should return status {int} for the contracts creation")
    public void verifyMirrorAPIResponse(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @Given("I verify the estimate precompile contract bytecode is deployed")
    public void contractDeployed() {
        var response = mirrorClient.getContractInfo(estimatePrecompileContractSolidityAddress);
        assertThat(response.getBytecode()).isNotBlank();
        assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    @Given("I create Fungible {token} token with custom fees")
    public void createFungibleToken(TokenNameEnum fungibleTokenName) {
        // Get Fungible token to be used as a denominating token for custom fees
        var fungibleToken = tokenClient.getToken(fungibleTokenName);
        networkTransactionResponse = fungibleToken.response();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(CUSTOM_FIXED_FEE_AMOUNT);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());
        customFixedFee.setDenominatingTokenId(fungibleToken.tokenId());

        CustomFractionalFee customFractionalFee = new CustomFractionalFee();
        customFractionalFee.setFeeCollectorAccountId(admin.getAccountId());
        customFractionalFee.setNumerator(1);
        customFractionalFee.setDenominator(10);
        customFractionalFee.setMax(100);

        List<CustomFee> fungibleFees = List.of(customFixedFee, customFractionalFee);

        // Crate Fungible token with custom fees
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var fungibleTokenWithCustomFeesResponse = tokenClient.getToken(fungibleTokenName, fungibleFees);
        networkTransactionResponse = fungibleTokenWithCustomFeesResponse.response();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Given("I create NFT {token} token with custom fees")
    public void createNFT(TokenNameEnum nftTokenName) {
        // Get Fungible token to be used as a denominating token for custom fees
        var fungibleToken = tokenClient.getToken(TokenNameEnum.FUNGIBLEHISTORICAL);
        networkTransactionResponse = fungibleToken.response();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(CUSTOM_FIXED_FEE_AMOUNT);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());
        customFixedFee.setDenominatingTokenId(fungibleToken.tokenId());

        CustomRoyaltyFee customRoyaltyFee = new CustomRoyaltyFee();
        customRoyaltyFee.setNumerator(5);
        customRoyaltyFee.setDenominator(10);
        customRoyaltyFee.setFallbackFee(
                new CustomFixedFee().setHbarAmount(new Hbar(1)).setDenominatingTokenId(fungibleToken.tokenId()));
        customRoyaltyFee.setFeeCollectorAccountId(admin.getAccountId());

        List<CustomFee> nonFungibleFees = List.of(customFixedFee, customRoyaltyFee);

        // Crate Non-Fungible token with custom fees
        var nftWithCustomFeesResponse = tokenClient.getToken(nftTokenName, nonFungibleFees);
        networkTransactionResponse = nftWithCustomFeesResponse.response();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I successfully update the contract storage and get the initial value via historical data")
    public void getHistoricalContractStorage() {
        var data = encodeData(ESTIMATE_GAS, GET_COUNTER);
        var initialResponse = callContract(data, estimateContractSolidityAddress);
        // the block number where contract storage variable is still with initial value
        var initialBlockNumber = getLastBlockNumber();

        waitForNextBlock();

        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(5));
        var maxGas = contractClient
                .getSdkClient()
                .getAcceptanceTestProperties()
                .getFeatureProperties()
                .getMaxContractFunctionGas();
        contractClient.executeContract(
                deployedEstimateContract.contractId(), maxGas, "updateCounter", parameters, null);

        var response = callContract(initialBlockNumber, data, estimateContractSolidityAddress);
        assertThat(initialResponse.getResultAsNumber()).isEqualTo(response.getResultAsNumber());
    }

    @Then("I successfully update the balance of an account and get the initial balance via historical data")
    public void getHistoricalBalance() {
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, receiverAccountAddress);
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(receiverAccountId.getAccountId(), Hbar.fromTinybars(50000000), null);
        var initialResponse = callContract(data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas())
                .getResultAsNumber();
        var initialBlockNumber = getLastBlockNumber();
        waitForNextBlock();
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(receiverAccountId.getAccountId(), Hbar.fromTinybars(50000000), null);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var response = callContract(
                        initialBlockNumber, data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas())
                .getResultAsNumber();
        assertThat(initialResponse).isEqualTo(response);
    }

    @Then("I verify that historical data for negative block returns bad request")
    public void getHistoricalDataForNegativeBlock() {
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, receiverAccountAddress);
        assertEthCallReturnsBadRequest("-100", data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas());
    }

    @Then("I verify that historical data for unknown block returns bad request")
    public void getHistoricalDataForUnknownBlock() {
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, receiverAccountAddress);
        var currentBlock = getLastBlockNumber();
        assertEthCallReturnsBadRequest(
                currentBlock + "0", data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas());
    }

    @Then("I verify that historical data for {string} block is treated as latest")
    public void getHistoricalData(String blockType) {
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, receiverAccountAddress);
        var responseFromType = callContract(
                        blockType, data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas())
                .getResultAsNumber();
        var responseFromLatest = callContract(data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas())
                .getResultAsNumber();
        assertThat(responseFromLatest).isEqualTo(responseFromType);
    }

    @RetryAsserts
    @Then("I verify the response from non existing account")
    public void getHistoricalDataForNonExistingAccount() {
        deletableAccountId = accountClient.getAccount(AccountNameEnum.DELETABLE);
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, asAddress(deletableAccountId.getAccountId()));

        waitUntilAccountIsImported(deletableAccountId.getAccountId());

        var initialResponse = callContract(data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas());
        var initialBlock = getLastBlockNumber();

        waitForNextBlock();

        networkTransactionResponse = accountClient.delete(deletableAccountId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse =
                callContract(initialBlock, data, estimateContractSolidityAddress, ADDRESS_BALANCE.getActualGas());
        assertThat(initialResponse).isEqualTo(historicalResponse);
    }

    @Then("I verify that historical data for {token} is returned via getTokenInfo")
    public void getHistoricalDataForTokenSymbol(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO, asAddress(tokenId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, precompileContractSolidityAddress, GET_TOKEN_INFO.getActualGas());
        final var trimmedResponse = trimTotalSupplyForGetTokenInfo(response.toString());

        waitForNextBlock();

        networkTransactionResponse = tokenClient.updateToken(tokenId, admin);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse = callContract(
                initialBlockNumber, data, precompileContractSolidityAddress, GET_TOKEN_INFO.getActualGas());
        final var historicalTrimmedResponse = trimTotalSupplyForGetTokenInfo(historicalResponse.toString());
        assertThat(trimmedResponse).isEqualTo(historicalTrimmedResponse);
    }

    @Then("I verify that historical data for {token} is returned via getTokenInfo when doing burn")
    public void getHistoricalDataForTokenInfoWhenDoingBurn(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO, asAddress(tokenId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, precompileContractSolidityAddress, GET_TOKEN_INFO.getActualGas());
        final var trimmedResponse = trimTotalSupplyForGetTokenInfo(response.toString());

        waitForNextBlock();

        networkTransactionResponse = tokenClient.burnFungible(tokenId, 5L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(
                initialBlockNumber, data, precompileContractSolidityAddress, GET_TOKEN_INFO.getActualGas());
        final var trimmedHistoricalResponse = trimTotalSupplyForGetTokenInfo(response.toString());

        assertThat(trimmedResponse).isEqualTo(trimmedHistoricalResponse);
    }

    @Then("I verify that historical data for {token} is returned via getTokenInfo when doing mint")
    public void getHistoricalDataForTokenInfoWhenDoingMint(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO, asAddress(tokenId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, precompileContractSolidityAddress, GET_TOKEN_INFO.getActualGas());
        final var trimmedResponse = trimTotalSupplyForGetTokenInfo(response.toString());
        waitForNextBlock();

        networkTransactionResponse = tokenClient.mint(tokenId, 5L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(
                initialBlockNumber, data, precompileContractSolidityAddress, GET_TOKEN_INFO.getActualGas());
        final var trimmedHistoricalResponse = trimTotalSupplyForGetTokenInfo(historicalResponse.toString());
        assertThat(trimmedResponse).isEqualTo(trimmedHistoricalResponse);
    }

    @Then("I mint new nft for {token}")
    public void mintNft(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I associate {token}")
    public void associateTokens(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        networkTransactionResponse = tokenClient.associate(receiverAccountId, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I grant KYC to {token} to receiver account")
    public void grantKyc(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        networkTransactionResponse = tokenClient.grantKyc(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify that historical data for {token} is returned via balanceOf")
    public void getHistoricalDataForBalanceOf(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeData(ERC, BALANCE_OF, asAddress(tokenId), adminAddress);
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, ercContractSolidityAddress, BALANCE_OF.getActualGas());
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();

        if (tokenName.name().toLowerCase().contains("fungible")) {
            networkTransactionResponse = tokenClient.transferFungibleToken(
                    tokenId,
                    tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                    receiverAccountId.getAccountId(),
                    receiverAccountId.getPrivateKey(),
                    10L);
        } else {
            tokenClient.mint(tokenId, "TEST_metadata".getBytes());
            networkTransactionResponse = tokenClient.transferNonFungibleToken(
                    tokenId,
                    admin,
                    receiverAccountId.getAccountId(),
                    List.of(2L),
                    receiverAccountId.getPrivateKey(),
                    null,
                    false);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse =
                callContract(initialBlockNumber, data, ercContractSolidityAddress, BALANCE_OF.getActualGas());
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertThat(initialBalance).isEqualTo(balanceOfHistorical);
    }

    @Then("I verify that historical data for {token} is returned via balanceOf by direct call")
    public void getHistoricalDataForBalanceOfDirectCall(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();

        var data = encodeData(BALANCE_OF_SELECTOR, adminAddress);
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, tokenId.toEvmAddress());
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();

        if (tokenName.name().toLowerCase().contains("fungible")) {
            networkTransactionResponse = tokenClient.mint(tokenId, 10L);
        } else {
            networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse =
                callContract(initialBlockNumber, data, asAddress(tokenId).toString());
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertThat(initialBalance).isEqualTo(balanceOfHistorical);
    }

    @Then("I verify that historical data for {token} is returned via balanceOf when doing burn")
    public void getHistoricalDataForBalanceOfWhenBurning(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeData(ERC, BALANCE_OF, asAddress(tokenId), adminAddress);
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, ercContractSolidityAddress, BALANCE_OF.getActualGas());
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();

        if (tokenName.name().toLowerCase().contains("fungible")) {
            tokenClient.burnFungible(tokenId, 5L);
        } else {
            tokenClient.burnNonFungible(tokenId, 3L);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse =
                callContract(initialBlockNumber, data, ercContractSolidityAddress, BALANCE_OF.getActualGas());
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertThat(initialBalance).isEqualTo(balanceOfHistorical);
    }

    @Then("I verify that historical data for {token} is returned via balanceOf when doing wipe")
    public void getHistoricalDataForBalanceOfWhenWiping(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeData(ERC, BALANCE_OF, asAddress(tokenId), receiverAccountAddress);
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, ercContractSolidityAddress, BALANCE_OF.getActualGas());
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();

        if (tokenName.name().toLowerCase().contains("fungible")) {
            tokenClient.wipeFungible(tokenId, 1L, receiverAccountId);
        } else {
            tokenClient.wipeNonFungible(tokenId, 2L, receiverAccountId);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse =
                callContract(initialBlockNumber, data, ercContractSolidityAddress, BALANCE_OF.getActualGas());
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertThat(initialBalance).isEqualTo(balanceOfHistorical);
    }

    @Then("I verify historical data for {token} is returned for allowance")
    public void getHistoricalDataForAllowance(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ESTIMATE_PRECOMPILE, ALLOWANCE, asAddress(tokenId), adminAddress, receiverAccountAddress);
        var response = callContract(data, estimatePrecompileContractSolidityAddress, ALLOWANCE.getActualGas());
        var initialAllowance = response.getResultAsNumber();

        waitForNextBlock();

        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 100L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse = callContract(
                initialBlockNumber, data, estimatePrecompileContractSolidityAddress, ALLOWANCE.getActualGas());
        var historicalAllowance = historicalResponse.getResultAsNumber();
        assertThat(initialAllowance).isEqualTo(historicalAllowance);
    }

    @Then("I verify historical data for {token} is returned for getApproved")
    public void getHistoricalDataForGetApproved(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var nftId = new NftId(tokenId, 1L);
        networkTransactionResponse = accountClient.approveNft(nftId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ESTIMATE_PRECOMPILE, GET_APPROVED, asAddress(tokenId), new BigInteger("1"));
        var response = callContract(data, estimatePrecompileContractSolidityAddress, GET_APPROVED.getActualGas());
        var initialApprovedAddress = response.getResultAsAddress();

        waitForNextBlock();

        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(
                initialBlockNumber, data, estimatePrecompileContractSolidityAddress, GET_APPROVED.getActualGas());
        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
        assertThat(initialApprovedAddress).isEqualTo(historicalApprovedAddress);
    }

    @Then("I verify historical data for {token} is returned for ERC allowance")
    public void getHistoricalDataForERCAllowance(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ERC, ALLOWANCE_SELECTOR, asAddress(tokenId), adminAddress, receiverAccountAddress);
        var response = callContract(data, ercContractSolidityAddress);
        var initialAllowance = response.getResultAsNumber();

        waitForNextBlock();

        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 150L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var historicalAllowance = historicalResponse.getResultAsNumber();
        assertThat(initialAllowance).isEqualTo(historicalAllowance);
    }

    @Then("I verify historical data for {token} is returned for ERC getApproved")
    public void getHistoricalDataForERCGetApproved(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var nftId = new NftId(tokenId, 1L);
        networkTransactionResponse = accountClient.approveNft(nftId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ERC, GET_APPROVED_SELECTOR, asAddress(tokenId), new BigInteger("1"));
        var response = callContract(data, ercContractSolidityAddress);
        var initialApprovedAddress = response.getResultAsAddress();

        waitForNextBlock();

        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
        assertThat(initialApprovedAddress).isEqualTo(historicalApprovedAddress);
    }

    @Then("I verify historical data for {token} is returned for allowance by direct call")
    public void getHistoricalDataForAllowanceDirectCall(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeData(ALLOWANCE_DIRECT_SELECTOR, adminAddress, receiverAccountAddress);
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, tokenId.toEvmAddress());
        var initialAllowance = response.getResultAsNumber();

        waitForNextBlock();

        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 200L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse =
                callContract(initialBlockNumber, data, asAddress(tokenId).toString());
        var historicalAllowance = historicalResponse.getResultAsNumber();
        assertThat(initialAllowance).isEqualTo(historicalAllowance);
    }

    @Then("I verify historical data for {token} is returned for getApproved direct call")
    public void getHistoricalDataForGetApprovedDirectCall(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var nftId = new NftId(tokenId, 1L);
        networkTransactionResponse = accountClient.approveNft(nftId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(GET_APPROVED_DIRECT_SELECTOR, new BigInteger("1"));
        var response = callContract(data, tokenId.toEvmAddress());
        var initialApprovedAddress = response.getResultAsAddress();

        waitForNextBlock();

        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse =
                callContract(initialBlockNumber, data, asAddress(tokenId).toString());
        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
        assertThat(initialApprovedAddress).isEqualTo(historicalApprovedAddress);
    }

    @Then("I verify historical data for {token} is returned for isApprovedForAll")
    public void getHistoricalDataForIsApprovedForAll(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(
                ESTIMATE_PRECOMPILE, IS_APPROVED_FOR_ALL, asAddress(tokenId), adminAddress, receiverAccountAddress);
        var response =
                callContract(data, estimatePrecompileContractSolidityAddress, IS_APPROVED_FOR_ALL.getActualGas());
        var initialResult = response.getResultAsAddress();

        waitForNextBlock();

        networkTransactionResponse = accountClient.approveNftAllSerials(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(
                initialBlockNumber,
                data,
                estimatePrecompileContractSolidityAddress,
                IS_APPROVED_FOR_ALL.getActualGas());
        var historicalResult = historicalResponse.getResultAsAddress();
        assertThat(initialResult).isEqualTo(historicalResult);
    }

    @Then("I verify historical data for {token} is returned for ownerOf")
    public void getHistoricalDataForOwnerOf(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ERC, GET_OWNER_OF_SELECTOR, asAddress(tokenId), new BigInteger("1"));
        var response = callContract(data, ercContractSolidityAddress);
        var initialOwner = response.getResultAsAddress();

        waitForNextBlock();

        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                tokenId,
                admin,
                receiverAccountId.getAccountId(),
                List.of(1L),
                receiverAccountId.getPrivateKey(),
                null,
                false);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var historicalOwner = historicalResponse.getResultAsAddress();
        assertThat(initialOwner).isEqualTo(historicalOwner);
    }

    @Then("I verify historical data for {token} is returned for isFrozen")
    public void getHistoricalDataForIsFrozenFungible(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeData(PRECOMPILE, IS_TOKEN_FROZEN_SELECTOR, asAddress(tokenId), receiverAccountAddress);
        var response = callContract(data, precompileContractSolidityAddress);
        var initialBlockNumber = getLastBlockNumber();
        var initialFreezeStatus = response.getResultAsBoolean();

        waitForNextBlock();

        networkTransactionResponse = tokenClient.freeze(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalFreezeStatus = callContract(initialBlockNumber, data, precompileContractSolidityAddress)
                .getResultAsBoolean();
        assertThat(initialFreezeStatus).isEqualTo(historicalFreezeStatus);

        // reverting to old state
        networkTransactionResponse = tokenClient.unfreeze(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify historical data for {token} is returned for getFungibleTokenInfo when doing {string}")
    public void getHistoricalDataForFungibleTokenInfo(TokenNameEnum tokenName, String action) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_FUNGIBLE_TOKEN_INFO, asAddress(tokenId));
        var response = callContract(data, precompileContractSolidityAddress, GET_FUNGIBLE_TOKEN_INFO.getActualGas());
        final var trimmedResponse = trimTotalSupplyForFungibleTokenInfo(response.toString());
        waitForNextBlock();

        switch (action) {
            case "update" -> networkTransactionResponse = tokenClient.updateToken(tokenId, admin);
            case "burn" -> networkTransactionResponse = tokenClient.burnFungible(tokenId, 10L);
            case "mint" -> networkTransactionResponse = tokenClient.mint(tokenId, 10L);
            case "wipe" -> {
                tokenClient.transferFungibleToken(
                        tokenId, admin, receiverAccountId.getAccountId(), receiverAccountId.getPrivateKey(), 10L);
                networkTransactionResponse = tokenClient.wipeFungible(tokenId, 1L, receiverAccountId);
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(
                initialBlockNumber, data, precompileContractSolidityAddress, GET_FUNGIBLE_TOKEN_INFO.getActualGas());
        final var trimmedHistoricalResponse = trimTotalSupplyForFungibleTokenInfo(response.toString());
        assertThat(trimmedResponse).isEqualTo(trimmedHistoricalResponse);
    }

    @Then(
            "I verify historical data for {token} is returned for getFungibleTokenInfo when doing {string} and transfer to {string}")
    public void getHistoricalDataForFungibleTokenInfoWhenTransferringToTreasury(
            TokenNameEnum tokenName, String action, String account) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_FUNGIBLE_TOKEN_INFO, asAddress(tokenId));
        var response = callContract(data, precompileContractSolidityAddress, GET_FUNGIBLE_TOKEN_INFO.getActualGas());
        final var trimmedResponse = trimTotalSupplyForFungibleTokenInfo(response.toString());

        waitForNextBlock();

        tokenClient.transferFungibleToken(tokenId, admin, receiverAccountId.getAccountId(), null, 10L);
        switch (action) {
            case "burn" -> networkTransactionResponse = tokenClient.burnFungible(tokenId, 10L);

            case "mint" -> networkTransactionResponse = tokenClient.mint(tokenId, 10L);

            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        }
        if (account.equals("treasury")) {
            networkTransactionResponse =
                    tokenClient.transferFungibleToken(tokenId, receiverAccountId, admin.getAccountId(), null, 10L);
        }
        if (account.equals("receiver")) {
            networkTransactionResponse = tokenClient.transferFungibleToken(
                    tokenId, admin, receiverAccountId.getAccountId(), receiverAccountId.getPrivateKey(), 10L);
        }
        if (account.equals("secondReceiver")) {
            tokenClient.associate(secondReceiverAccountId, tokenId);
            tokenClient.grantKyc(tokenId, secondReceiverAccountId.getAccountId());
            networkTransactionResponse = tokenClient.transferFungibleToken(
                    tokenId,
                    receiverAccountId,
                    secondReceiverAccountId.getAccountId(),
                    secondReceiverAccountId.getPrivateKey(),
                    10L);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(
                initialBlockNumber, data, precompileContractSolidityAddress, GET_FUNGIBLE_TOKEN_INFO.getActualGas());
        final var trimmedHistoricalResponse = trimTotalSupplyForFungibleTokenInfo(response.toString());
        assertThat(trimmedResponse).isEqualTo(trimmedHistoricalResponse);
    }

    @Then("I verify historical data for {token} is returned for getNonFungibleInfo when doing {string}")
    public void getHistoricalDataForNonFungibleTokenInfo(TokenNameEnum tokenName, String action) {
        String data;
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        if (action.equals("wipe")) {
            data = encodeData(PRECOMPILE, GET_NON_FUNGIBLE_TOKEN_INFO, asAddress(tokenId), 5L);
        } else {
            data = encodeData(PRECOMPILE, GET_NON_FUNGIBLE_TOKEN_INFO, asAddress(tokenId), 4L);
        }
        var response =
                callContract(data, precompileContractSolidityAddress, GET_NON_FUNGIBLE_TOKEN_INFO.getActualGas());

        waitForNextBlock();

        switch (action) {
            case "mint" -> networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
            case "burn" -> networkTransactionResponse = tokenClient.burnNonFungible(tokenId, 4L);
            case "wipe" -> {
                tokenClient.transferNonFungibleToken(
                        tokenId, admin, receiverAccountId.getAccountId(), List.of(5L), null, null, false);
                networkTransactionResponse = tokenClient.wipeNonFungible(tokenId, 5L, receiverAccountId);
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(
                initialBlockNumber,
                data,
                precompileContractSolidityAddress,
                GET_NON_FUNGIBLE_TOKEN_INFO.getActualGas());
        assertThat(response).isEqualTo(historicalResponse);
    }

    @And("I update the token and account keys for {token}")
    public void updateAccountAndTokenKeys(TokenNameEnum tokenName)
            throws PrecheckStatusException, TimeoutException, ReceiptStatusException {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var keyList = KeyList.of(admin.getPublicKey(), deployedEstimatePrecompileContract.contractId())
                .setThreshold(1);
        new AccountUpdateTransaction()
                .setAccountId(admin.getAccountId())
                .setKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        var tokenUpdate = new TokenUpdateTransaction()
                .setTokenId(tokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        networkTransactionResponse = new NetworkTransactionResponse(
                tokenUpdate.transactionId, tokenUpdate.getReceipt(accountClient.getClient()));
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify historical data for {token} is returned for getTokenKey")
    public void getHistoricalDataForGetTokenKey(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        BigInteger[] tokenKeyValues = {
            new BigInteger("1"), new BigInteger("2"), new BigInteger("4"), new BigInteger("8"), new BigInteger("16")
        };
        var currentResponses = new HashMap<BigInteger, Object>();

        // Collect current responses
        for (BigInteger keyValue : tokenKeyValues) {
            var data = encodeData(ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(tokenId), keyValue);
            currentResponses.put(
                    keyValue,
                    callContract(data, estimatePrecompileContractSolidityAddress, GET_TOKEN_KEY.getActualGas()));
        }

        waitForNextBlock();

        // Perform update
        var updateData = encodeDataToByteArray(ESTIMATE_PRECOMPILE, UPDATE_TOKEN_KEYS, asAddress(tokenId));
        var result = contractClient.executeContract(
                deployedEstimatePrecompileContract.contractId(), 3000000, "updateTokenInfoExternal", updateData, null);
        networkTransactionResponse = result.networkTransactionResponse();
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        // Collect and compare historical responses
        for (BigInteger keyValue : tokenKeyValues) {
            var data = encodeData(ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(tokenId), keyValue);
            var historicalResponse = callContract(
                    initialBlockNumber, data, estimatePrecompileContractSolidityAddress, GET_TOKEN_KEY.getActualGas());
            assertThat(currentResponses).containsEntry(keyValue, historicalResponse);
        }
    }

    @Then("I verify historical data for {token} is returned for isKyc")
    public void getHistoricalDataForIsKyc(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, IS_KYC_GRANTED_SELECTOR, asAddress(tokenId), receiverAccountAddress);
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        networkTransactionResponse = tokenClient.revokeKyc(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertThat(response).isEqualTo(historicalResponse);

        // reverting the old state
        networkTransactionResponse = tokenClient.grantKyc(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify historical data for {token} is returned for get token operations")
    public void getHistoricalDataForGetTokenOperations(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        Selector[] selectors = {
            new Selector(GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR, PRECOMPILE, precompileContractSolidityAddress),
            new Selector(GET_TOKEN_DEFAULT_FREEZE_SELECTOR, PRECOMPILE, precompileContractSolidityAddress),
            new Selector(GET_TOKEN_DEFAULT_KYC_SELECTOR, PRECOMPILE, precompileContractSolidityAddress),
            new Selector(GET_TOKEN_TYPE, PRECOMPILE, precompileContractSolidityAddress),
            new Selector(GET_TOKEN_EXPIRY_INFO, ESTIMATE_PRECOMPILE, estimatePrecompileContractSolidityAddress),
            new Selector(IS_TOKEN, ESTIMATE_PRECOMPILE, estimatePrecompileContractSolidityAddress)
        };

        // collect current responses
        var currentResponses = new HashMap<Selector, Object>();
        for (Selector selector : selectors) {
            var data = encodeData(selector.resource(), selector.selector(), asAddress(tokenId));
            if (isEstimatePrecompileFeatureContractMethod(selector)) {
                currentResponses.put(
                        selector,
                        callContract(
                                data,
                                selector.contractAddress(),
                                ((EstimatePrecompileFeature.ContractMethods) selector.selector()).getActualGas()));
            } else {
                currentResponses.put(selector, callContract(data, selector.contractAddress()));
            }
        }

        waitForNextBlock();

        // deleting the token to change the state
        networkTransactionResponse = tokenClient.delete(admin, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        // verifying the historical responses against the initial responses
        for (Selector selector : selectors) {
            var data = encodeData(selector.resource(), selector.selector(), asAddress(tokenId));
            ContractCallResponseWrapper historicalResponse = null;
            if (isEstimatePrecompileFeatureContractMethod(selector)) {
                historicalResponse = callContract(
                        initialBlockNumber,
                        data,
                        selector.contractAddress(),
                        ((EstimatePrecompileFeature.ContractMethods) selector.selector()).getActualGas());
            } else {
                historicalResponse = callContract(initialBlockNumber, data, selector.contractAddress());
            }
            assertThat(currentResponses).containsEntry(selector, historicalResponse);
        }

        // recreating the deleted token
        var tokenResponse = tokenClient.getToken(tokenName);
        networkTransactionResponse = tokenResponse.response();
    }

    @Then("I verify historical data for {token} in invalid block returns bad request")
    public void getHistoricalDataNonExistingToken(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        networkTransactionResponse = tokenClient.delete(admin, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        // creating the new token and getting the creation block
        var initialBlockNumber = getLastBlockNumber();
        var previousBlock = String.valueOf(Long.parseLong(initialBlockNumber) - 5);
        var tokenResponse = tokenClient.getToken(tokenName);
        networkTransactionResponse = tokenResponse.response();
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO, asAddress(tokenResponse.tokenId()));
        assertEthCallReturnsBadRequest(
                previousBlock, data, estimatePrecompileContractSolidityAddress, GET_TOKEN_INFO.getActualGas());
    }

    private String getLastBlockNumber() {
        return mirrorClient
                .getBlocks(Order.DESC, 1)
                .getBlocks()
                .getFirst()
                .getNumber()
                .toString();
    }

    private void waitForNextBlock() {
        int currentBlockNumber = Integer.parseInt(getLastBlockNumber());

        try {
            Awaitility.await()
                    .atMost(3, TimeUnit.SECONDS)
                    .pollInterval(250, TimeUnit.MILLISECONDS)
                    .ignoreExceptions()
                    .until(() -> Integer.parseInt(getLastBlockNumber()) > currentBlockNumber);
        } catch (ConditionTimeoutException e) {
            log.info("No new block found within 3 seconds");
        }
    }

    private void waitUntilAccountIsImported(AccountId accountId) {
        try {
            Awaitility.await()
                    .atMost(3, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .ignoreExceptions()
                    .until(() -> mirrorClient
                                    .getAccountDetailsUsingEvmAddress(accountId)
                                    .getEvmAddress()
                            != null);
        } catch (ConditionTimeoutException e) {
            log.info("The account could not be imported in the mirror node for 3 seconds.");
        }
    }

    private boolean isEstimatePrecompileFeatureContractMethod(final Selector selector) {
        return selector.selector()
                .getClass()
                .getCanonicalName()
                .equals("org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods");
    }

    // The query for totalSupply historical depends on the db tables - token_balance, token_transfers and
    // account_balance and since they update on every 15 minutes we need this retry to ensure the totalSupply is
    // calculated correctly. That way we avoid flakiness of this test that might occur if the db tables are not
    // updated and the environment is clear
    private String trimTotalSupplyForGetTokenInfo(String response) {
        var responseWithoutPrefix = "";
        if (response.startsWith(HEX_PREFIX)) {
            responseWithoutPrefix = response.substring(2);
        }
        // TotalSupply value is located between 128 and 192 indexes
        int startIndex = 128;
        int endIndex = startIndex + 64;

        // Check if the response without prefix is with correct size
        if (responseWithoutPrefix.length() != 7168) {
            throw new IllegalArgumentException("Invalid response size.");
        }

        return responseWithoutPrefix.substring(0, startIndex) + responseWithoutPrefix.substring(endIndex);
    }

    // The query for totalSupply historical depends on the db tables - token_balance, token_transfers and
    // account_balance and since they update on every 15 minutes we need this retry to ensure the totalSupply is
    // calculated correctly. That way we avoid flakiness of this test that might occur if the db tables are not
    // updated and the environment is clear
    private String trimTotalSupplyForFungibleTokenInfo(String response) {
        var responseWithoutPrefix = "";
        if (response.startsWith(HEX_PREFIX)) {
            responseWithoutPrefix = response.substring(2);
        }
        // TotalSupply value is located between 258 and 322 indexes
        int startIndex = 258;
        int endIndex = startIndex + 64;

        // Check if the response without prefix is with correct size
        if (responseWithoutPrefix.length() != 7296) {
            throw new IllegalArgumentException("Invalid response size.");
        }

        return responseWithoutPrefix.substring(0, startIndex) + responseWithoutPrefix.substring(endIndex);
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        GET_COUNTER("counter");

        private final String selector;
    }

    record Selector(SelectorInterface selector, ContractResource resource, String contractAddress) {}
}
