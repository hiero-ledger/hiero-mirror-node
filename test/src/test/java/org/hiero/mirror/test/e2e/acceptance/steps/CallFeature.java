// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.aspectj.runtime.internal.Conversions.intValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_FOR_ETH_CALL;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_KYC_UNFROZEN_FOR_ETH_CALL;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT_FOR_ETH_CALL;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.MUTABLE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.VIEW;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.ADDRESS_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.APPROVE_NFT_TOKEN_AND_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.APPROVE_TOKEN_GET_ALLOWANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DISSOCIATE_TOKEN_FAIL_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.FREEZE_UNFREEZE_GET_STATUS;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.GRANT_KYC_REVOKE_KYC;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_FROZEN_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_KYC_GRANTED_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_TOKEN_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_BALANCE_OF_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_NAME_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_SYMBOL_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.PAUSE_UNPAUSE_GET_STATUS;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.REENTRANCY_CALL_WITH_GAS;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.STATE_UPDATE_N_TIMES_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.UPDATE_COUNTER_SELECTOR;
import static org.hiero.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asByteArray;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asLongArray;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import java.util.List;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient;
import org.hiero.mirror.test.e2e.acceptance.config.Web3Properties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;

@CustomLog
@RequiredArgsConstructor
public class CallFeature extends AbstractFeature {

    private static final String HEX_REGEX = "^[0-9a-fA-F]+$";
    private static DeployedContract deployedPrecompileContract;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private final Web3Properties web3Properties;
    private DeployedContract deployedErcTestContract;
    private DeployedContract deployedEstimatePrecompileContract;
    private String ercContractAddress;
    private String precompileContractAddress;
    private String estimateContractAddress;
    private ExpandedAccountId receiverAccountId;
    private Address receiverAccountAddress;
    private ExpandedAccountId secondReceiverAccount;
    private ExpandedAccountId thirdReceiver;
    private String secondReceiverAlias;
    private TokenId fungibleTokenId;
    private Address fungibleTokenAddress;
    private TokenId nonFungibleTokenId;
    private Address nonFungibleTokenAddress;
    private TokenId fungibleKycUnfrozenTokenId;
    private Address fungibleKycUnfrozenTokenAddress;
    private ExpandedAccountId admin;
    private Address adminAddress;

    public static String[] splitAddresses(String result) {
        // remove the '0x' prefix
        String strippedResult = result.substring(2);

        // split into two addresses
        String address1 = strippedResult.substring(0, 64);
        String address2 = strippedResult.substring(64);

        // remove leading zeros and add '0x' prefix back
        address1 = new BigInteger(address1, 16).toString(16);
        address2 = new BigInteger(address2, 16).toString(16);

        return new String[] {address1, address2};
    }

    @RetryAsserts
    @Then("the mirror node should return status {int} for the HAPI transaction")
    public void ethCallVerifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Given("I successfully create ERC contract")
    public void createNewERCtestContract() {
        deployedErcTestContract = getContract(ERC);
        ercContractAddress = deployedErcTestContract.contractId().toEvmAddress();
    }

    @Given("I successfully create Precompile contract")
    public void createNewPrecompileTestContract() {
        deployedPrecompileContract = getContract(PRECOMPILE);
        precompileContractAddress =
                asAddress(deployedPrecompileContract.contractId()).toString();
    }

    @Given("I successfully create EstimateGas contract")
    public void createNewEstimateTestContract() {
        deployedEstimatePrecompileContract = getContract(ESTIMATE_GAS);
        estimateContractAddress =
                deployedEstimatePrecompileContract.contractId().toEvmAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        adminAddress = asAddress(admin);
        receiverAccountId = accountClient.getAccount(AccountNameEnum.ALICE);
        receiverAccountAddress = asAddress(receiverAccountId);
        secondReceiverAccount = accountClient.getAccount(AccountNameEnum.BOB);
        thirdReceiver = accountClient.getAccount(AccountNameEnum.DAVE);
        secondReceiverAlias =
                secondReceiverAccount.getPublicKey().toEvmAddress().toString();
        fungibleTokenId = tokenClient.getToken(FUNGIBLE_FOR_ETH_CALL).tokenId();
        fungibleTokenAddress = asAddress(fungibleTokenId);
        fungibleKycUnfrozenTokenId =
                tokenClient.getToken(FUNGIBLE_KYC_UNFROZEN_FOR_ETH_CALL).tokenId();
        fungibleKycUnfrozenTokenAddress = asAddress(fungibleKycUnfrozenTokenId);
        nonFungibleTokenId = tokenClient.getToken(NFT_FOR_ETH_CALL).tokenId();
        nonFungibleTokenAddress = asAddress(nonFungibleTokenId);
    }

    @Given("I mint a NFT")
    public void mintNft() {
        networkTransactionResponse = tokenClient.mint(nonFungibleTokenId, nextBytes(4));
    }

    @And("I approve and transfer FUNGIBLE token to receiver account")
    public void approveAndTransferFungibleTokenToReceiver() {
        accountClient.approveToken(fungibleTokenId, receiverAccountId.getAccountId(), 1L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                receiverAccountId.getAccountId(),
                receiverAccountId.getPrivateKey(),
                1L);
    }

    @And("I transfer FUNGIBLE token to the precompile contract")
    public void approveAndTransferFungibleTokenToPrecompile() {
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                admin,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()),
                receiverAccountId.getPrivateKey(),
                10L);
    }

    @And("I approve and transfer NFT token to receiver account")
    public void approveAndTransferNftTokenToReceiver() {
        var ntfId = new NftId(nonFungibleTokenId, 1L);
        accountClient.approveNft(ntfId, receiverAccountId.getAccountId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                receiverAccountId.getAccountId(),
                List.of(1L),
                receiverAccountId.getPrivateKey(),
                null,
                false);
    }

    @And("I associate FUNGIBLE token to receiver account")
    public void associateFungibleTokenToReceiver() {
        tokenClient.associate(receiverAccountId, fungibleTokenId);
    }

    @And("I associate NFT token to receiver account")
    public void associateNftTokenToReceiver() {
        networkTransactionResponse = tokenClient.associate(receiverAccountId, nonFungibleTokenId);
    }

    @And("I associate precompile contract with the tokens")
    public void associatePrecompileWithTokens() {
        // In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedPrecompileContract.contractId(), fungibleTokenId);
        networkTransactionResponse = tokenClient.associate(deployedPrecompileContract.contractId(), nonFungibleTokenId);
    }

    @And("I associate FUNGIBLE_KYC_UNFROZEN token to receiver account")
    public void associateReceiverWithFungibleKyc() {
        networkTransactionResponse = tokenClient.associate(secondReceiverAccount, fungibleKycUnfrozenTokenId);
    }

    @And("I approve and transfer NFT token to the precompile contract")
    public void approveAndTransferNftToPrecompileContract() throws InvalidProtocolBufferException {
        accountClient.approveNftAllSerials(nonFungibleTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                receiverAccountId,
                TestUtil.fromSolidityAddress(precompileContractAddress),
                List.of(1L),
                null,
                null,
                false);
    }

    // ETHCALL-017
    @RetryAsserts
    @Then("I call function with IERC721Metadata token {string} name")
    public void ierc721MetadataTokenName(String tokenName) {
        var tokenNameEnum = TokenClient.TokenNameEnum.valueOf(tokenName);
        var tokenId = tokenClient.getToken(tokenNameEnum).tokenId();

        var data = encodeData(ERC, IERC721_TOKEN_NAME_SELECTOR, asAddress(tokenId));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsText()).isEqualTo(tokenNameEnum.getSymbol() + "_name");
    }

    // ETHCALL-018
    @RetryAsserts
    @Then("I call function with IERC721Metadata token {string} symbol")
    public void ierc721MetadataTokenSymbol(String tokenName) {
        var tokenNameEnum = TokenClient.TokenNameEnum.valueOf(tokenName);
        var tokenId = tokenClient.getToken(tokenNameEnum).tokenId();

        var data = encodeData(ERC, IERC721_TOKEN_SYMBOL_SELECTOR, asAddress(tokenId));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsText()).isEqualTo(tokenNameEnum.getSymbol());
    }

    // ETHCALL-019
    @RetryAsserts
    @Then("I call function with IERC721Metadata token NFT totalSupply")
    public void ierc721MetadataTokenTotalSupply() {
        var totalSupplyOfNft =
                mirrorClient.getTokenInfo(nonFungibleTokenId.toString()).getTotalSupply();

        var data = encodeData(ERC, IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR, nonFungibleTokenAddress);
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(totalSupplyOfNft);
    }

    // ETHCALL-020
    @RetryAsserts
    @Then("I call function with IERC721 token NFT balanceOf owner")
    public void ierc721MetadatagetBalanceOfTokenTokenBalanceOf() {
        var balanceOfNft = getBalanceOfToken(nonFungibleTokenId, admin.getAccountId());
        var data = encodeData(
                ERC,
                IERC721_TOKEN_BALANCE_OF_SELECTOR,
                nonFungibleTokenAddress,
                asAddress(contractClient.getClientAddress()));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(balanceOfNft);
    }

    @RetryAsserts
    @Given("I verify the precompile contract bytecode is deployed")
    public void contractDeployed() {
        var response = mirrorClient.getContractInfo(precompileContractAddress);
        assertThat(response.getBytecode()).isNotBlank();
        assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    // ETHCALL-025
    @RetryAsserts
    @Then("I call function with HederaTokenService isToken token FUNGIBLE")
    public void htsIsToken() {
        var data = encodeData(PRECOMPILE, HTS_IS_TOKEN_SELECTOR, fungibleTokenAddress);
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isTrue();
    }

    // ETHCALL-026
    @RetryAsserts
    @Then("I call function with HederaTokenService isFrozen token FUNGIBLE, account")
    public void htsIsFrozen() {
        var data = encodeData(
                PRECOMPILE, HTS_IS_FROZEN_SELECTOR, fungibleTokenAddress, asAddress(contractClient.getClientAddress()));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-027
    @RetryAsserts
    @Then("I call function with HederaTokenService isKyc token FUNGIBLE, account")
    public void htsIsKyc() {
        var data = encodeData(
                PRECOMPILE,
                HTS_IS_KYC_GRANTED_SELECTOR,
                fungibleTokenAddress,
                asAddress(contractClient.getClientAddress()));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isTrue();
    }

    // ETHCALL-028
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultFreezeStatus token FUNGIBLE")
    public void htsGetTokenDefaultFreezeStatus() {
        var data = encodeData(PRECOMPILE, HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR, fungibleTokenAddress);
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-029
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultKycStatus token FUNGIBLE")
    public void htsGetTokenDefaultKycStatus() {
        var data = encodeData(PRECOMPILE, HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR, fungibleTokenAddress);
        var response = callContract(data, precompileContractAddress);
        boolean defaultKycStatus = false;
        // In the modularized code, the status is now true when the token has a KycNotApplicable status,
        // whereas the mono logic returns false. We need to toggle the status based on the modularized flag.
        if (web3Properties.isModularizedServices()) {
            defaultKycStatus = !defaultKycStatus;
        }

        assertThat(response.getResultAsBoolean()).isEqualTo(defaultKycStatus);
    }

    @Then("I call function with update and I expect return of the updated value")
    public void ethCallUpdateFunction() {
        var updateValue = new BigInteger("5");
        var data = encodeData(ESTIMATE_GAS, UPDATE_COUNTER_SELECTOR, updateValue);
        var response = callContract(data, estimateContractAddress);

        assertEquals(response.getResultAsNumber(), updateValue);
    }

    @Then("I call function that makes N times state update")
    public void ethCallStateUpdateNTimesFunction() {
        var data = encodeData(ESTIMATE_GAS, STATE_UPDATE_N_TIMES_SELECTOR, new BigInteger("15"));
        var response = callContract(data, estimateContractAddress);

        assertEquals("14", String.valueOf(response.getResultAsNumber()));
    }

    @Then("I call function with nested deploy using create function")
    public void ethCallNestedDeployViaCreateFunction() {
        var data = encodeData(ESTIMATE_GAS, DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR);
        var response = callContract(data, estimateContractAddress);
        String[] addresses = splitAddresses(response.getResult());

        validateAddresses(addresses);
    }

    @Then("I call function with nested deploy using create2 function")
    public void ethCallNestedDeployViaCreate2Function() {
        var data = encodeData(ESTIMATE_GAS, DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR);
        var response = callContract(data, estimateContractAddress);

        String[] addresses = splitAddresses(response.getResult());

        validateAddresses(addresses);
    }

    @SuppressWarnings("java:S2925")
    @RetryAsserts
    @Then("I successfully update the balance of an account and get the updated balance after 2 seconds")
    public void getBalance() throws InterruptedException {
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, receiverAccountAddress);
        var initialBalance = callContract(data, estimateContractAddress).getResultAsNumber();
        networkTransactionResponse = accountClient.sendCryptoTransfer(
                receiverAccountId.getAccountId(),
                Hbar.fromTinybars(initialBalance.longValue()),
                receiverAccountId.getPrivateKey());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        // wait for token cache to expire
        Thread.sleep(2000);
        var updatedBalance = callContract(data, estimateContractAddress).getResultAsNumber();
        assertThat(initialBalance).isEqualTo(updatedBalance.divide(BigInteger.TWO));
    }

    @RetryAsserts
    @Then("I call function with transfer that returns the balance")
    public void ethCallReentrancyCallFunction() {
        var data = encodeData(ESTIMATE_GAS, REENTRANCY_CALL_WITH_GAS, receiverAccountAddress, new BigInteger("10000"));
        var response = callContract(data, estimateContractAddress);
        String[] balances = splitAddresses(response.getResult());
        // verify initial balance
        assertEquals(1000000, Integer.parseInt(balances[0], 16));
        // verify balance after transfer of 10,000
        assertEquals(990000, Integer.parseInt(balances[1], 16));
    }

    @Then("I directly call Ethereum precompile 0x01")
    public void directCallTowardsEthereumPrecompileECRecover() {
        final var precompileAddress = "0x0000000000000000000000000000000000000001";
        final var hash = "0x456e9aea5e197a1f1af7a3e85a3212fa4049a3ba34c2289b4c860fc0b0c64ef3";
        final var v = "000000000000000000000000000000000000000000000000000000000000001c";
        final var r = "9242685bf161793cc25603c231bc2f568eb630ea16aa137d2664ac8038825608";
        final var s = "4f8ae3bd7535248d0bd448298cc2e2071e56992d0774dc340c368ae950852ada";
        final var correctResult = "0x0000000000000000000000007156526fbd7a3c72969b54f64e42c10fbb768c8a";

        final var data = hash.concat(v).concat(r).concat(s);

        var response = callContract(data, precompileAddress);
        assertThat(response.getResult()).isEqualTo(correctResult);
    }

    @Then("I directly call Ethereum precompile 0x02")
    public void directCallTowardsEthereumPrecompileSHA256() {
        final var precompileAddress = "0x0000000000000000000000000000000000000002";
        final var data = "0xFF";
        final var correctResult = "0xa8100ae6aa1940d0b663bb31cd466142ebbdbd5187131b92d93818987832eb89";

        var response = callContract(data, precompileAddress);
        assertThat(response.getResult()).isEqualTo(correctResult);
    }

    @Then("I mint FUNGIBLE token and get the total supply and balance")
    public void ethCallMintFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                fungibleTokenAddress,
                1L,
                new byte[][] {},
                adminAddress);

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) + 1)
                .as("BalanceBefore + amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) + 1L)
                .as("totalSupplyBefore + amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I mint NFT token and get the total supply and balance")
    public void ethCallMintNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                nonFungibleTokenAddress,
                0L,
                asByteArray(List.of("0x02")),
                asAddress(tokenClient
                        .getSdkClient()
                        .getExpandedOperatorAccountId()
                        .getAccountId()));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) + 1)
                .as("BalanceBefore + amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) + 1)
                .as("totalSupplyBefore + amount = totaSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I burn FUNGIBLE token and get the total supply and balance")
    public void ethCallBurnFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                fungibleTokenAddress,
                1L,
                asLongArray(List.of()),
                adminAddress);

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(intValue(results.get(0)) - 1L))
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(intValue(results.get(2)) - 1L))
                .as("totalSupplyBefore - amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Retryable(
            retryFor = {HttpClientErrorException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    @Then("I burn NFT and get the total supply and balance")
    public void ethCallBurnNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                nonFungibleTokenAddress,
                0L,
                asLongArray(List.of(1L)),
                adminAddress);

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) - 1)
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) - 1)
                .as("totalSupplyBefore - amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I wipe FUNGIBLE token and get the total supply and balance")
    public void ethCallWipeFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                fungibleTokenAddress,
                1L,
                asLongArray(List.of()),
                receiverAccountAddress);

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(intValue(results.get(0)) - 1L))
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(intValue(results.get(2)) - 1L))
                .as("totalSupplyBefore - amount = totaSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I wipe NFT and get the total supply and balance")
    public void ethCallWipeNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                nonFungibleTokenAddress,
                0L,
                asLongArray(List.of(1L)),
                receiverAccountAddress);

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) - 1)
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) - 1)
                .as("totalSupplyBefore - amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I pause {string} token, unpause and get the status of the token")
    public void ethCallPauseTokenGetStatusUnpauseGetStatus(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();
        var data = encodeData(PRECOMPILE, PAUSE_UNPAUSE_GET_STATUS, asAddress(tokenId));
        var response = callContract(data, precompileContractAddress);
        var statusAfterPause = response.getResult().substring(2, 66);
        var statusAfterUnpause = response.getResult().substring(66);

        assertThat(Integer.valueOf(statusAfterPause, 16))
                .as("isPaused after pause is true")
                .isEqualTo(1);
        assertThat(Integer.valueOf(statusAfterUnpause, 16))
                .as("isPaused after unpause is false")
                .isZero();
    }

    @Then("I freeze {string} token, unfreeze and get status")
    public void ethCallFreezeFungibleGetFreezeStatusUnfreezeGetFreezeStatus(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();
        var data = encodeData(PRECOMPILE, FREEZE_UNFREEZE_GET_STATUS, asAddress(tokenId), adminAddress);
        var response = callContract(data, precompileContractAddress);
        var statusAfterFreeze = response.getResult().substring(2, 66);
        var statusAfterUnfreeze = response.getResult().substring(66);

        assertThat(Integer.valueOf(statusAfterFreeze, 16))
                .as("isFreezed after freeze is true")
                .isEqualTo(1);
        assertThat(Integer.valueOf(statusAfterUnfreeze, 16))
                .as("isFreezed after unfreeze is false")
                .isZero();
    }

    @Then("I approve a FUNGIBLE token and get allowance")
    public void ethCallApproveFungibleTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_TOKEN_GET_ALLOWANCE,
                fungibleTokenAddress,
                asAddress(secondReceiverAlias),
                new BigInteger("1"),
                new BigInteger("0"));

        var response = callContract(data, precompileContractAddress);
        var allowance = response.getResult().substring(2, 66);

        assertThat(new BigInteger(allowance))
                .as("allowance should equal amount")
                .isEqualTo(BigInteger.valueOf(1L));
    }

    @Then("I approve a NFT token and get allowance")
    public void ethCallApproveNFTTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_TOKEN_GET_ALLOWANCE,
                nonFungibleTokenAddress,
                receiverAccountAddress,
                new BigInteger("0"),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var approvedAddress = response.getResult().substring(66);

        assertThat(approvedAddress)
                .as("approved address should equal the spender")
                .isEqualTo(to32BytesString(receiverAccountAddress.toString().toLowerCase()));
    }

    @Then("I dissociate a FUNGIBLE token and fail transfer")
    public void ethCallAssociateFungibleTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_TOKEN_FAIL_TRANSFER,
                fungibleTokenAddress,
                adminAddress,
                asAddress(thirdReceiver),
                new BigInteger("1"),
                new BigInteger("0"));
        var response = callContract(data, precompileContractAddress, 2_000_000);
        var resultList = response.getResultAsListDecimal();
        var statusAfterAssociate = resultList.get(0);
        var statusAfterDissociate = resultList.get(1);

        assertThat(statusAfterAssociate)
                .as("transfer after associate should pass -> response code 22 equals SUCCESS")
                .isEqualTo(22);
        assertThat(statusAfterDissociate)
                .as("transfer after dissociate should fail > response code 184 equals to owner does not own the token")
                .isEqualTo(184);
    }

    @Then("I dissociate a NFT and fail transfer")
    public void ethCallAssociateNftTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_TOKEN_FAIL_TRANSFER,
                nonFungibleTokenAddress,
                receiverAccountAddress,
                asAddress(secondReceiverAlias),
                new BigInteger("0"),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress, 2_000_000);
        var resultList = response.getResultAsListDecimal();
        var statusAfterAssociate = resultList.get(0);
        var statusAfterDissociate = resultList.get(1);

        assertThat(statusAfterAssociate)
                .as("transfer after associate should pass -> response code 22 equals SUCCESS")
                .isEqualTo(22);
        assertThat(statusAfterDissociate)
                .as("transfer after dissociate should fail -> response code 237 equals to owner does not own the NFT")
                .isEqualTo(184);
    }

    @Then("I approve a FUNGIBLE token and transfer it")
    public void ethCallApproveFungibleTokenAndTransfer() {
        networkTransactionResponse = tokenClient.associate(thirdReceiver, fungibleTokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var data = encodeData(
                PRECOMPILE,
                APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER,
                fungibleTokenAddress,
                asAddress(thirdReceiver),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)))
                .as("allowance before transfer should equal the amount")
                .isZero();
        assertThat(intValue(results.get(1)) + 1)
                .as("balance before + amount should equal the balance after")
                .isEqualTo(intValue(results.get(3)));
        assertThat(intValue(results.get(2)))
                .as("allowance after transfer should be 0")
                .isZero();
    }

    @Then("I approve a NFT token and transfer it")
    public void ethCallApproveNftTokenAndTransfer() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_NFT_TOKEN_AND_TRANSFER,
                nonFungibleTokenAddress,
                receiverAccountAddress,
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListAddress();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(results.get(0))
                .as("allowed address before transfer should be the receiverAccount")
                .isEqualTo(results.get(3));
        assertThat(results.get(1))
                .as("owner after transfer should be the Precompile")
                .isEqualTo(to32BytesString(results.get(1)));
        assertThat(results.get(2))
                .as("allowance after transfer should be 0")
                .isEqualTo("0000000000000000000000000000000000000000000000000000000000000000");
    }

    @Then("I grant and revoke KYC")
    public void ethCallGrantKycRevokeKyc() {
        networkTransactionResponse = tokenClient.associate(secondReceiverAccount, fungibleKycUnfrozenTokenId);
        var data = encodeData(
                PRECOMPILE,
                GRANT_KYC_REVOKE_KYC,
                fungibleKycUnfrozenTokenAddress,
                adminAddress,
                asAddress(secondReceiverAlias),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(5);

        assertThat(results.get(0)).as("isKYC after grant should be true").isEqualTo(1);
        assertThat(results.get(1)).as("KYC grant status should be SUCCESS = 22").isEqualTo(22);
        assertThat(results.get(2)).as("isKYC after revoke should be false").isZero();
        assertThat(results.get(3))
                .as("KYC revoke status should be SUCCESS = 22")
                .isEqualTo(22);
        assertThat(results.get(4))
                .as("transfer status after kyc revert should be failing with KYC should be granted")
                .isEqualTo(176);
    }

    private void validateAddresses(String[] addresses) {
        assertNotEquals(addresses[0], addresses[1]);
        assertTrue(addresses[0].matches(HEX_REGEX));
        assertTrue(addresses[1].matches(HEX_REGEX));
    }

    private long getBalanceOfToken(TokenId tokenId, AccountId accountId) {
        var tokenRelationships =
                mirrorClient.getTokenRelationships(accountId, tokenId).getTokens();
        assertThat(tokenRelationships).isNotNull().hasSize(1);
        return tokenRelationships.get(0).getBalance();
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        IERC721_TOKEN_NAME_SELECTOR("nameIERC721", VIEW),
        IERC721_TOKEN_SYMBOL_SELECTOR("symbolIERC721", VIEW),
        IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR("totalSupplyIERC721", VIEW),
        IERC721_TOKEN_BALANCE_OF_SELECTOR("balanceOfIERC721", VIEW),
        HTS_IS_TOKEN_SELECTOR("isTokenAddress", VIEW),
        HTS_IS_FROZEN_SELECTOR("isTokenFrozen", VIEW),
        HTS_IS_KYC_GRANTED_SELECTOR("isKycGranted", VIEW),
        HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR("getTokenDefaultFreeze", VIEW),
        HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR("getTokenDefaultKyc", VIEW),
        UPDATE_COUNTER_SELECTOR("updateCounter", MUTABLE),
        STATE_UPDATE_N_TIMES_SELECTOR("updateStateNTimes", MUTABLE),
        DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR("deployNestedContracts", MUTABLE),
        DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR("deployNestedContracts2", MUTABLE),
        REENTRANCY_CALL_WITH_GAS("reentrancyCallWithGas", MUTABLE),
        MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", MUTABLE),
        BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", MUTABLE),
        WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", MUTABLE),
        PAUSE_UNPAUSE_GET_STATUS("pauseTokenGetPauseStatusUnpauseGetPauseStatus", MUTABLE),
        FREEZE_UNFREEZE_GET_STATUS("freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus", MUTABLE),
        APPROVE_TOKEN_GET_ALLOWANCE("approveTokenGetAllowance", MUTABLE),
        DISSOCIATE_TOKEN_FAIL_TRANSFER("associateTokenDissociateFailTransfer", MUTABLE),
        APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER("approveFungibleTokenTransferFromGetAllowanceGetBalance", MUTABLE),
        APPROVE_NFT_TOKEN_AND_TRANSFER("approveNftAndTransfer", MUTABLE),
        GRANT_KYC_REVOKE_KYC("grantKycRevokeKyc", MUTABLE),
        ADDRESS_BALANCE("addressBalance", VIEW);

        private final String selector;
        private final FunctionType functionType;
    }
}
