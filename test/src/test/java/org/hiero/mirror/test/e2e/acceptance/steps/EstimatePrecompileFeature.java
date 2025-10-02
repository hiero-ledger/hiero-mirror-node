// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_KYC_UNFROZEN;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT_KYC_UNFROZEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.MUTABLE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.PAYABLE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.VIEW;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ALLOWANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ALLOWANCE_ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_FUNGIBLE_GET_ALLOWANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_NFT_GET_ALLOWANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_NFT_TOKEN_AND_TRANSFER_FROM;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ASSOCIATE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ASSOCIATE_TOKENS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BALANCE_OF;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BURN_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_FUNGIBLE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CRYPTO_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CRYPTO_TRANSFER_HBARS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CRYPTO_TRANSFER_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DECIMALS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DELETE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_AND_ASSOCIATE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_NFT_AND_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_TOKENS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.EXCHANGE_RATE_TINYBARS_TO_TINYCENTS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.EXCHANGE_RATE_TINYCENTS_TO_TINYBARS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.FREEZE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.FREEZE_UNFREEZE_GET_STATUS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_APPROVED;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_APPROVED_ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_FUNGIBLE_TOKEN_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_NON_FUNGIBLE_TOKEN_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_EXPIRY_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_INFO_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_KEY;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_TYPE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GRANT_KYC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_APPROVED_FOR_ALL;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_APPROVED_FOR_ALL_ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_FROZEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_KYC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NAME;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NAME_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NESTED_ASSOCIATE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NESTED_FREEZE_UNFREEZE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NESTED_GRANT_REVOKE_KYC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.OWNER_OF;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PAUSE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PAUSE_UNPAUSE_GET_STATUS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PSEUDO_RANDOM_NUMBER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PSEUDO_RANDOM_SEED;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_ALLOWANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_APPROVE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_BALANCE_OF;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_DECIMALS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_GET_OWNER_OF;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_NAME;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_SYMBOL;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TOKEN_URI;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REVOKE_KYC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.SET_APPROVAL_FOR_ALL;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.SYMBOL;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.SYMBOL_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TOKEN_URI;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TOTAL_SUPPLY;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TOTAL_SUPPLY_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_FROM;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_FROM_ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_FROM_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_NFT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_NFTS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_TOKENS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UNFREEZE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UNPAUSE_TOKEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UPDATE_TOKEN_EXPIRY;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UPDATE_TOKEN_INFO;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UPDATE_TOKEN_KEYS;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_NFT_ACCOUNT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_TOKEN_ACCOUNT;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.*;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Strings;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.rest.model.ContractCallResponse;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import org.hiero.mirror.test.e2e.acceptance.config.Web3Properties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;

@CustomLog
@RequiredArgsConstructor
public class EstimatePrecompileFeature extends AbstractEstimateFeature {
    private static final Tuple[] EMPTY_TUPLE_ARRAY = new Tuple[] {};
    private static final long FIRST_NFT_SERIAL_NUMBER = 1;
    private static final long NUM_MAX_SIZE = 274877906943L;
    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private final Web3Properties web3Properties;
    private TokenId fungibleKycUnfrozenTokenId;
    private TokenId nonFungibleKycUnfrozenTokenId;
    private Address fungibleKycUnfrozenTokenAddress;
    private Address nonFungibleKycUnfrozenTokenAddress;

    private TokenId fungibleTokenId;
    private TokenId nonFungibleTokenId;
    private Address fungibleTokenAddress;
    private Address nonFungibleTokenAddress;
    private DeployedContract deployedEstimatePrecompileContract;
    private DeployedContract deployedErcTestContract;
    private DeployedContract deployedPrecompileContract;
    private ExpandedAccountId receiverAccount;
    private String receiverAccountAlias;
    private Address receiverAccountAliasAddress;
    private ExpandedAccountId secondReceiverAccount;
    private Address secondReceiverAccountAddress;
    private ExpandedAccountId admin;
    private Address adminAddress;
    private String estimatePrecompileContractSolidityAddress;
    private String ercTestContractSolidityAddress;
    private String precompileTestContractSolidityAddress;

    @Given("I create estimate precompile contract with 0 balance")
    public void createNewEstimateContract() {
        deployedEstimatePrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        estimatePrecompileContractSolidityAddress =
                deployedEstimatePrecompileContract.contractId().toEvmAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        adminAddress = asAddress(admin);
        receiverAccount = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        secondReceiverAccount = accountClient.getAccount(AccountNameEnum.DAVE);
        secondReceiverAccountAddress = asAddress(secondReceiverAccount);
        receiverAccountAlias = receiverAccount.getPublicKey().toEvmAddress().toString();
        receiverAccountAliasAddress = asAddress(receiverAccountAlias);
    }

    @Given("I create erc test contract with 0 balance")
    public void createNewERCContract() {
        deployedErcTestContract = getContract(ERC);
        ercTestContractSolidityAddress = deployedErcTestContract.contractId().toEvmAddress();
    }

    @Given("I get exchange rates")
    public void getExchangeRate() {
        exchangeRates = mirrorClient.getExchangeRates();
    }

    @Given("I successfully create Precompile contract with 0 balance")
    public void createNewPrecompileTestContract() {
        deployedPrecompileContract = getContract(PRECOMPILE);
        precompileTestContractSolidityAddress =
                deployedPrecompileContract.contractId().toEvmAddress();
    }

    @Given("I successfully create fungible tokens")
    public void createFungibleToken() {
        fungibleKycUnfrozenTokenId = tokenClient.getToken(FUNGIBLE_KYC_UNFROZEN).tokenId();
        fungibleTokenId = tokenClient.getToken(FUNGIBLE).tokenId();
        fungibleKycUnfrozenTokenAddress = asAddress(fungibleKycUnfrozenTokenId);
        fungibleTokenAddress = asAddress(fungibleTokenId);
    }

    @Given("I successfully create non fungible tokens")
    public void createNonFungibleToken() {
        nonFungibleKycUnfrozenTokenId = tokenClient.getToken(NFT_KYC_UNFROZEN).tokenId();
        nonFungibleTokenId = tokenClient.getToken(NFT).tokenId();
        nonFungibleKycUnfrozenTokenAddress = asAddress(nonFungibleKycUnfrozenTokenId);
        nonFungibleTokenAddress = asAddress(nonFungibleTokenId);
    }

    @Given("I mint and verify a new nft")
    public void mintNft() {
        tokenClient.mint(nonFungibleTokenId, nextBytes(4));
        networkTransactionResponse = tokenClient.mint(nonFungibleKycUnfrozenTokenId, nextBytes(4));
    }

    @Then("the mirror node REST API should return status {int} for the HAPI transaction")
    public void verifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Then("I verify the mirror node REST API returns contract states for deployed contract")
    public void verifyContractStatesAPIResponse() {
        var contractStates = mirrorClient.getContractStatesById(
                deployedEstimatePrecompileContract.contractId().toString(), 15);
        assertThat(contractStates.getState().size()).isEqualTo(11);
    }

    @And("I set lower deviation at {int}% and upper deviation at {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    @Then("I call estimateGas with associate function for fungible token")
    public void associateFunctionEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, ASSOCIATE_TOKEN, receiverAccountAliasAddress, fungibleTokenAddress);
        validateGasEstimation(data, ASSOCIATE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with associate function for NFT")
    public void associateFunctionNFTEstimateGas() {
        var data =
                encodeData(ESTIMATE_PRECOMPILE, ASSOCIATE_TOKEN, receiverAccountAliasAddress, nonFungibleTokenAddress);
        validateGasEstimation(data, ASSOCIATE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with dissociate token function without association for fungible token")
    public void dissociateFunctionEstimateGasNegative() {
        // attempt to call dissociate function without having association
        // expecting status 400/revert
        var data = encodeData(ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, receiverAccountAliasAddress, fungibleTokenAddress);

        assertContractCallReturnsBadRequest(
                data, DISSOCIATE_TOKEN.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function without association for NFT")
    public void dissociateFunctionNFTEstimateGasNegative() {
        // attempt to call dissociate function without having association
        // expecting status 400/revert
        var data =
                encodeData(ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, receiverAccountAliasAddress, nonFungibleTokenAddress);

        assertContractCallReturnsBadRequest(
                data, DISSOCIATE_TOKEN.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for fungible token")
    public void nestedAssociateFunctionEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        var data = encodeData(ESTIMATE_PRECOMPILE, NESTED_ASSOCIATE, receiverAccountAliasAddress, fungibleTokenAddress);

        assertContractCallReturnsBadRequest(
                data, NESTED_ASSOCIATE.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for NFT")
    public void nestedAssociateFunctionNFTEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        var data =
                encodeData(ESTIMATE_PRECOMPILE, NESTED_ASSOCIATE, receiverAccountAliasAddress, nonFungibleTokenAddress);

        assertContractCallReturnsBadRequest(
                data, NESTED_ASSOCIATE.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the receiver account with the fungible token")
    public void associateReceiverWithFungibleEstimateGas() {
        // associating the token with the token address
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for fungible token")
    public void dissociateFunctionEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, receiverAccountAliasAddress, fungibleTokenAddress);

        validateGasEstimation(data, DISSOCIATE_TOKEN, deployedEstimatePrecompileContract);
    }

    @And("I associate the receiver account with the NFT")
    public void associateReceiverWithNonFungibleEstimateGas() {
        // associating the NFT with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for NFT")
    public void dissociateFunctionNFTEstimateGas() {
        var data =
                encodeData(ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, receiverAccountAliasAddress, nonFungibleTokenAddress);

        validateGasEstimation(data, DISSOCIATE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with dissociate and associate nested function for fungible token")
    public void dissociateAndAssociatedEstimateGas() {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        var data = encodeData(
                ESTIMATE_PRECOMPILE, DISSOCIATE_AND_ASSOCIATE, receiverAccountAliasAddress, fungibleTokenAddress);

        validateGasEstimation(data, DISSOCIATE_AND_ASSOCIATE, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with dissociate and associate nested function for NFT")
    public void dissociateAndAssociatedNFTEstimateGas() {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        var data = encodeData(
                ESTIMATE_PRECOMPILE, DISSOCIATE_AND_ASSOCIATE, receiverAccountAliasAddress, nonFungibleTokenAddress);

        validateGasEstimation(data, DISSOCIATE_AND_ASSOCIATE, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with approve function without association")
    public void approveWithoutAssociationEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, APPROVE, fungibleTokenAddress, receiverAccountAliasAddress, new BigInteger("10"));

        assertContractCallReturnsBadRequest(data, APPROVE.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with setApprovalForAll function without association")
    public void setApprovalForAllWithoutAssociationEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, SET_APPROVAL_FOR_ALL, nonFungibleTokenAddress, receiverAccountAliasAddress, true);

        assertContractCallReturnsBadRequest(
                data, SET_APPROVAL_FOR_ALL.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approveNFT function without association")
    public void approveNonFungibleWithoutAssociationEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                APPROVE_NFT,
                nonFungibleTokenAddress,
                receiverAccountAliasAddress,
                new BigInteger("1"));

        assertContractCallReturnsBadRequest(data, APPROVE_NFT.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I associate contracts with the tokens and approve all nft serials")
    public void associateTokensWithContract() throws InvalidProtocolBufferException {
        // In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedErcTestContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), nonFungibleKycUnfrozenTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), nonFungibleTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), nonFungibleKycUnfrozenTokenId);

        // approve is also needed for the approveNFT function
        accountClient.approveNftAllSerials(nonFungibleKycUnfrozenTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = accountClient.approveNftAllSerials(
                nonFungibleKycUnfrozenTokenId, deployedEstimatePrecompileContract.contractId());
    }

    @Then("I call estimateGas with ERC approve function")
    public void ercApproveEstimateGas() {
        var data =
                encodeData(ERC, APPROVE_ERC, fungibleTokenAddress, receiverAccountAliasAddress, new BigInteger("10"));

        validateGasEstimation(data, APPROVE_ERC, deployedErcTestContract);
    }

    @Then("I call estimateGas with setApprovalForAll function")
    public void setApprovalForAllEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                SET_APPROVAL_FOR_ALL,
                nonFungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress,
                true);

        validateGasEstimation(data, SET_APPROVAL_FOR_ALL, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with transferFrom function without approval")
    public void transferFromEstimateGasWithoutApproval() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM,
                fungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("5"));

        assertContractCallReturnsBadRequest(data, TRANSFER_FROM.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function without approval")
    public void ercTransferFromEstimateGasWithoutApproval() {
        var data = encodeData(
                ERC,
                TRANSFER_FROM_ERC,
                fungibleTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("10"));

        assertContractCallReturnsBadRequest(data, TRANSFER_FROM_ERC.actualGas, ercTestContractSolidityAddress);
    }

    @And("I approve the contract to use fungible token")
    public void approveFungibleWithReceiver() {
        final var ercTestContractId = TestUtil.fromSolidityAddress(ercTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, ercTestContractId, 10);
        final var precompileTestContractId = TestUtil.fromSolidityAddress(precompileTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, precompileTestContractId, 10);
    }

    @Then("I call estimateGas with ERC transferFrom function")
    public void ercTransferFromEstimateGas() {
        var data = encodeData(
                ERC,
                TRANSFER_FROM_ERC,
                fungibleTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("5"));

        validateGasEstimation(data, TRANSFER_FROM_ERC, deployedErcTestContract);
    }

    @Then("I call estimateGas with transferFrom function with more than the approved allowance")
    public void transferFromExceedAllowanceEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM,
                fungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("500"));

        assertContractCallReturnsBadRequest(data, TRANSFER_FROM.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function with more than the approved allowance")
    public void ercTransferFromExceedsAllowanceEstimateGas() {
        var data = encodeData(
                ERC,
                TRANSFER_FROM_ERC,
                fungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("500"));

        assertContractCallReturnsBadRequest(data, TRANSFER_FROM_ERC.actualGas, ercTestContractSolidityAddress);
    }

    @And("I approve receiver account to use the NFT with id 1")
    public void approveNonFungibleWithReceiver() {
        NftId id = new NftId(nonFungibleTokenId, FIRST_NFT_SERIAL_NUMBER);
        networkTransactionResponse = accountClient.approveNft(id, receiverAccount.getAccountId());
    }

    @Then("I call estimateGas with transferFromNFT with invalid serial number")
    public void transferFromNFTInvalidSerialEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM_NFT,
                nonFungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("50"));

        assertContractCallReturnsBadRequest(
                data, TRANSFER_FROM_NFT.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferNFT function")
    public void transferNFTEstimateGas() {
        var methodInterface = getFlaggedValue(TRANSFER_NFT);
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                nonFungibleTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                1L);

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @And("I approve the receiver account to use fungible token and transfer fungible token to the erc contract")
    public void approveAndTransferFungibleToken() {
        accountClient.approveToken(fungibleTokenId, receiverAccount.getAccountId(), 50L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                admin,
                AccountId.fromString(deployedErcTestContract.contractId().toString()),
                null,
                10);
    }

    @Then("I call estimateGas with ERC transfer function")
    public void ercTransferEstimateGas() {
        var data =
                encodeData(ERC, TRANSFER_ERC, fungibleTokenAddress, receiverAccountAliasAddress, new BigInteger("5"));

        validateGasEstimation(data, TRANSFER_ERC, deployedErcTestContract);
    }

    @Then("I call estimateGas with associateTokens function for fungible tokens")
    public void associateTokensEstimateGas() {
        var methodInterface = getFlaggedValue(ASSOCIATE_TOKENS);
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                secondReceiverAccountAddress,
                asAddressArray(
                        Arrays.asList(fungibleTokenAddress.toString(), fungibleKycUnfrozenTokenAddress.toString())));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with associateTokens function for NFTs")
    public void associateNFTEstimateGas() {
        var methodInterface = getFlaggedValue(ASSOCIATE_TOKENS);
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                secondReceiverAccountAddress,
                asAddressArray(Arrays.asList(
                        nonFungibleKycUnfrozenTokenAddress.toString(), nonFungibleTokenAddress.toString())));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @And("I associate the fungible_kyc_unfrozen token with the receiver account")
    public void associateFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for fungible tokens")
    public void dissociateTokensEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                DISSOCIATE_TOKENS,
                receiverAccountAliasAddress,
                asAddressArray(
                        Arrays.asList(fungibleTokenAddress.toString(), fungibleKycUnfrozenTokenAddress.toString())));

        validateGasEstimation(data, DISSOCIATE_TOKENS, deployedEstimatePrecompileContract);
    }

    @And("I associate the nft_kyc_unfrozen with the receiver account")
    public void associateNonFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for NFTs")
    public void dissociateNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                DISSOCIATE_TOKENS,
                receiverAccountAliasAddress,
                asAddressArray(Arrays.asList(
                        nonFungibleKycUnfrozenTokenAddress.toString(), nonFungibleTokenAddress.toString())));

        validateGasEstimation(data, DISSOCIATE_TOKENS, deployedEstimatePrecompileContract);
    }

    @And("I associate and approve the second receiver to use the fungible_kyc_unfrozen token")
    public void associateAndApproveFungibleKycUnfrozenTokenWithReceiverAccount() {
        tokenClient.associate(secondReceiverAccount, fungibleTokenId);
        networkTransactionResponse =
                accountClient.approveToken(fungibleTokenId, secondReceiverAccount.getAccountId(), 10);
    }

    @Then("I call estimateGas with transferTokens function")
    public void transferTokensEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_TOKENS,
                fungibleTokenAddress,
                asAddressArray(Arrays.asList(
                        adminAddress.toString(), receiverAccountAlias, secondReceiverAccountAddress.toString())),
                new long[] {-6L, 3L, 3L});

        validateGasEstimation(data, TRANSFER_TOKENS, deployedEstimatePrecompileContract);
    }

    @And("I mint a new NFT and approve second receiver account to all serial numbers")
    public void mintAndApproveAllSerialsToSecondReceiver() {
        tokenClient.mint(nonFungibleTokenId, nextBytes(4));
        accountClient.approveNftAllSerials(nonFungibleTokenId, receiverAccount.getAccountId());
        tokenClient.associate(secondReceiverAccount, nonFungibleTokenId);
        networkTransactionResponse =
                accountClient.approveNftAllSerials(nonFungibleTokenId, secondReceiverAccount.getAccountId());
    }

    @Then("I call estimateGas with transferNFTs function")
    public void transferNFTsEstimateGas() {
        final var sendersList = new LinkedList<>(List.of(adminAddress.toString()));
        if (web3Properties.isModularizedServices()) {
            // In the modularized scenario the number of senders needs to correspond to the number of receivers.
            sendersList.add(adminAddress.toString());
        }
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_NFTS,
                nonFungibleTokenAddress,
                asAddressArray(sendersList),
                asAddressArray(Arrays.asList(receiverAccountAlias, secondReceiverAccountAddress.toString())),
                new long[] {1, 2});

        validateGasEstimation(data, TRANSFER_NFTS, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with cryptoTransfer function for hbars")
    public void cryptoTransferHbarEstimateGas() {
        var senderTransfer = accountAmount(adminAddress.toString(), -10L, false);
        var receiverTransfer = accountAmount(receiverAccountAliasAddress.toString(), 10L, false);
        var args = Tuple.from((Object) new Tuple[] {senderTransfer, receiverTransfer});
        var data = encodeData(ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER_HBARS, args, EMPTY_TUPLE_ARRAY);
        validateGasEstimation(data, CRYPTO_TRANSFER_HBARS, deployedEstimatePrecompileContract);
    }

    private TokenTransferListBuilder tokenTransferList() {
        return new TokenTransferListBuilder();
    }

    @Then("I call estimateGas with cryptoTransfer function for nft")
    public void cryptoTransferNFTEstimateGas() {
        var methodInterface = getFlaggedValue(CRYPTO_TRANSFER_NFT);
        var tokenTransferList = (Object) new Tuple[] {
            tokenTransferList()
                    .forToken(nonFungibleTokenAddress.toString())
                    .withNftTransfers(
                            nftAmount(adminAddress.toString(), receiverAccountAliasAddress.toString(), 1L, false))
                    .build()
        };
        var data = encodeData(
                ESTIMATE_PRECOMPILE, methodInterface, Tuple.from((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with cryptoTransfer function for fungible tokens")
    public void cryptoTransferFungibleEstimateGas() {
        var tokenTransferList = (Object) new Tuple[] {
            tokenTransferList()
                    .forToken(fungibleTokenAddress.toString())
                    .withAccountAmounts(
                            accountAmount(adminAddress.toString(), -3L, false),
                            accountAmount(secondReceiverAccountAddress.toString(), 3L, false))
                    .build()
        };
        var data = encodeData(
                ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER, Tuple.from((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(data, CRYPTO_TRANSFER, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with burnToken function for fungible token")
    public void burnFungibleTokenEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, BURN_TOKEN, fungibleKycUnfrozenTokenAddress, 1L, asLongArray(new ArrayList<>()));

        validateGasEstimation(data, BURN_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with burnToken function for NFT")
    public void burnNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, BURN_TOKEN, nonFungibleKycUnfrozenTokenAddress, 0L, asLongArray(List.of(1L)));

        validateGasEstimation(data, BURN_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with CreateFungibleToken function")
    public void createFungibleTokenEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, CREATE_FUNGIBLE_TOKEN, adminAddress);

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_FUNGIBLE_TOKEN.getActualGas(), calculateCreateTokenFee(1, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function")
    public void createNFTEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, CREATE_NFT, adminAddress);

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_NFT.getActualGas(), calculateCreateTokenFee(1, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateFungibleToken function with custom fees")
    public void createFungibleTokenWithCustomFeesEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES,
                adminAddress,
                fungibleKycUnfrozenTokenAddress);

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas(), calculateCreateTokenFee(2, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function with custom fees")
    public void createNFTWithCustomFeesEstimateGas() {
        // The custom fee denomination must be fungible token in modularized services.
        var data = encodeData(ESTIMATE_PRECOMPILE, CREATE_NFT_WITH_CUSTOM_FEES, adminAddress, fungibleTokenAddress);

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_NFT_WITH_CUSTOM_FEES.getActualGas(), calculateCreateTokenFee(2, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @And("I approve and transfer fungible tokens to receiver account")
    public void approveAndTransferFungibleTokensToReceiverAccount() {
        accountClient.approveToken(fungibleTokenId, receiverAccount.getAccountId(), 100L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId, admin, receiverAccount.getAccountId(), receiverAccount.getPrivateKey(), 10L);
    }

    @Then("I call estimateGas with WipeTokenAccount function")
    public void wipeTokenAccountEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, WIPE_TOKEN_ACCOUNT, fungibleTokenAddress, receiverAccountAliasAddress, 1L);

        validateGasEstimation(data, WIPE_TOKEN_ACCOUNT, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with WipeTokenAccount function with invalid amount")
    public void wipeTokenAccountInvalidAmountEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                WIPE_TOKEN_ACCOUNT,
                fungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress,
                100000000000000000L);

        assertContractCallReturnsBadRequest(
                data, WIPE_TOKEN_ACCOUNT.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @And("I transfer NFT to receiver account")
    public void transferNonFungibleToReceiverAccount() {
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                admin,
                receiverAccount.getAccountId(),
                Collections.singletonList(FIRST_NFT_SERIAL_NUMBER),
                receiverAccount.getPrivateKey(),
                null,
                false);
    }

    @Then("I call estimateGas with WipeNFTAccount function")
    public void wipeNFTAccountEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                WIPE_NFT_ACCOUNT,
                nonFungibleTokenAddress,
                receiverAccountAliasAddress,
                asLongArray(List.of(1L)));

        validateGasEstimation(data, WIPE_NFT_ACCOUNT, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with WipeNFTAccount function with invalid serial number")
    public void wipeNFTAccountInvalidSerialNumberEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                WIPE_NFT_ACCOUNT,
                nonFungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress,
                asLongArray(List.of(66L)));

        assertContractCallReturnsBadRequest(
                data, WIPE_NFT_ACCOUNT.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for fungible token")
    public void grantKYCFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GRANT_KYC, fungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, GRANT_KYC, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with GrantKYC function for NFT")
    public void grantKYCNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GRANT_KYC, nonFungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, GRANT_KYC, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with RevokeTokenKYC function for fungible token")
    public void revokeTokenKYCEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, REVOKE_KYC, fungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, REVOKE_KYC, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with RevokeTokenKYC function for NFT")
    public void revokeTokenKYCNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, REVOKE_KYC, nonFungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, REVOKE_KYC, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with Grant and Revoke KYC nested function")
    public void nestedGrantRevokeKYCEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                NESTED_GRANT_REVOKE_KYC,
                fungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress);

        validateGasEstimation(data, NESTED_GRANT_REVOKE_KYC, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with Freeze function for fungible token")
    public void freezeFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, FREEZE_TOKEN, fungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, FREEZE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with Freeze function for NFT")
    public void freezeNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, FREEZE_TOKEN, nonFungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, FREEZE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with Unfreeze function for fungible token")
    public void unfreezeFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, UNFREEZE_TOKEN, fungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, UNFREEZE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with Unfreeze function for NFT")
    public void unfreezeNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, UNFREEZE_TOKEN, nonFungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, UNFREEZE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for fungible token")
    public void nestedFreezeAndUnfreezeEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                NESTED_FREEZE_UNFREEZE,
                fungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress);

        validateGasEstimation(data, NESTED_FREEZE_UNFREEZE, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for NFT")
    public void nestedFreezeAndUnfreezeNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                NESTED_FREEZE_UNFREEZE,
                nonFungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress);

        validateGasEstimation(data, NESTED_FREEZE_UNFREEZE, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with delete function for Fungible token")
    public void deleteFungibleEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, DELETE_TOKEN, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, DELETE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with delete function for NFT")
    public void deleteNFTEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, DELETE_TOKEN, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, DELETE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with delete function for invalid token address")
    public void deleteTokenInvalidAddressEstimateGas() {
        String address = Hex.toHexString(ByteBuffer.allocate(20)
                .putInt(0)
                .putLong(0)
                .putLong(new SecureRandom().nextLong(NUM_MAX_SIZE / 100, NUM_MAX_SIZE))
                .array());

        var data = encodeData(ESTIMATE_PRECOMPILE, DELETE_TOKEN, asAddress(address));

        assertContractCallReturnsBadRequest(data, DELETE_TOKEN.actualGas, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pause function for fungible token")
    public void pauseFungibleTokenPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PAUSE_TOKEN, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, PAUSE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with pause function for NFT")
    public void pauseNFTPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PAUSE_TOKEN, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, PAUSE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with unpause function for fungible token")
    public void unpauseFungibleTokenPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UNPAUSE_TOKEN, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, UNPAUSE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with unpause function for NFT")
    public void unpauseNFTPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UNPAUSE_TOKEN, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, UNPAUSE_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas for nested pause and unpause function")
    public void pauseUnpauseFungibleTokenNestedCallEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PAUSE_UNPAUSE_NESTED_TOKEN, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, PAUSE_UNPAUSE_NESTED_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas for nested pause, unpause NFT function")
    public void pauseUnpauseNFTNestedCallEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PAUSE_UNPAUSE_NESTED_TOKEN, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, PAUSE_UNPAUSE_NESTED_TOKEN, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with updateTokenExpiryInfo function")
    public void updateTokenExpiryInfoEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UPDATE_TOKEN_EXPIRY, fungibleKycUnfrozenTokenAddress, adminAddress);

        validateGasEstimation(data, UPDATE_TOKEN_EXPIRY, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with updateTokenInfo function")
    public void updateTokenInfoEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UPDATE_TOKEN_INFO, fungibleKycUnfrozenTokenAddress, adminAddress);

        validateGasEstimation(data, UPDATE_TOKEN_INFO, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with updateTokenKeys function")
    public void updateTokenKeysEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UPDATE_TOKEN_KEYS, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, UPDATE_TOKEN_KEYS, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenExpiryInfo function")
    public void getTokenExpiryInfoEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_EXPIRY_INFO);
        var data = encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with isToken function")
    public void isTokenEstimateGas() {
        var methodInterface = getFlaggedValue(IS_TOKEN);
        var data = encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenKey function for supply")
    public void getTokenKeySupplyEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var data =
                encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, new BigInteger("16"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenKey function for KYC")
    public void getTokenKeyKYCEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var data =
                encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, new BigInteger("2"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenKey function for freeze")
    public void getTokenKeyFreezeEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var data =
                encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, new BigInteger("4"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenKey function for admin")
    public void getTokenKeyAdminEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var data =
                encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, new BigInteger("1"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenKey function for wipe")
    public void getTokenKeyWipeEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var data =
                encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, new BigInteger("8"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenKey function for fee")
    public void getTokenKeyFeeEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var data =
                encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, new BigInteger("32"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with getTokenKey function for pause")
    public void getTokenKeyPauseEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var data =
                encodeData(ESTIMATE_PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, new BigInteger("64"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with ERC allowance function for fungible token")
    public void ercAllowanceFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(ALLOWANCE_ERC);
        var data = encodeData(
                ERC, methodInterface, fungibleKycUnfrozenTokenAddress, adminAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with getApproved function for NFT")
    public void getApprovedNonFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(GET_APPROVED);
        var data = encodeData(
                ESTIMATE_PRECOMPILE, methodInterface, nonFungibleKycUnfrozenTokenAddress, new BigInteger("1"));

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with ERC getApproved function for NFT")
    public void ercGetApprovedNonFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(GET_APPROVED_ERC);
        var data = encodeData(ERC, methodInterface, nonFungibleKycUnfrozenTokenAddress, new BigInteger("1"));

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with isApprovedForAll function")
    public void isApprovedForAllEstimateGas() {
        var methodInterface = getFlaggedValue(IS_APPROVED_FOR_ALL);
        // reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                nonFungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with ERC isApprovedForAll function")
    public void ercIsApprovedForAllEstimateGas() {
        var methodInterface = getFlaggedValue(IS_APPROVED_FOR_ALL_ERC);
        // reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        var data = encodeData(
                ERC, methodInterface, nonFungibleKycUnfrozenTokenAddress, adminAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with name function for fungible token")
    public void nameEstimateGas() {
        var data = encodeData(ERC, NAME, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, NAME, deployedErcTestContract);
    }

    @Then("I call estimateGas with name function for NFT")
    public void nameNonFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(NAME_NFT);
        var data = encodeData(ERC, methodInterface, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with symbol function for fungible token")
    public void symbolEstimateGas() {
        var methodInterface = getFlaggedValue(SYMBOL);
        var data = encodeData(ERC, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with symbol function for NFT")
    public void symbolNonFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(SYMBOL_NFT);
        var data = encodeData(ERC, methodInterface, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with decimals function for fungible token")
    public void decimalsEstimateGas() {
        var methodInterface = getFlaggedValue(DECIMALS);
        var data = encodeData(ERC, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with totalSupply function for fungible token")
    public void totalSupplyEstimateGas() {
        var methodInterface = getFlaggedValue(TOTAL_SUPPLY);
        var data = encodeData(ERC, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with totalSupply function for NFT")
    public void totalSupplyNonFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(TOTAL_SUPPLY_NFT);
        var data = encodeData(ERC, methodInterface, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with ownerOf function for NFT")
    public void ownerOfEstimateGas() {
        var methodInterface = getFlaggedValue(OWNER_OF);
        var data = encodeData(ERC, methodInterface, nonFungibleKycUnfrozenTokenAddress, new BigInteger("1"));

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with tokenURI function for NFT")
    public void tokenURIEstimateGas() {
        var methodInterface = getFlaggedValue(TOKEN_URI);
        var data = encodeData(ERC, methodInterface, nonFungibleKycUnfrozenTokenAddress, new BigInteger("1"));

        validateGasEstimation(data, methodInterface, deployedErcTestContract);
    }

    @Then("I call estimateGas with getFungibleTokenInfo function")
    public void getFungibleTokenInfoEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_FUNGIBLE_TOKEN_INFO, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, GET_FUNGIBLE_TOKEN_INFO, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getNonFungibleTokenInfo function")
    public void getNonFungibleTokenInfoEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_NON_FUNGIBLE_TOKEN_INFO, nonFungibleKycUnfrozenTokenAddress, 1L);

        validateGasEstimation(data, GET_NON_FUNGIBLE_TOKEN_INFO, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenInfo function for fungible")
    public void getTokenInfoEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, GET_TOKEN_INFO, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenInfo function for NFT")
    public void getTokenInfoNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO_NFT, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, GET_TOKEN_INFO_NFT, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for fungible token")
    public void getTokenDefaultFreezeStatusFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_FREEZE_STATUS);
        var data = encodeData(PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for NFT")
    public void getTokenDefaultFreezeStatusNonFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_FREEZE_STATUS);
        var data = encodeData(PRECOMPILE, methodInterface, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for fungible token")
    public void getTokenDefaultKycStatusFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_KYC_STATUS);
        var data = encodeData(PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for NFT")
    public void getTokenDefaultKycStatusNonFungibleEstimateGas() {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_KYC_STATUS);
        var data = encodeData(PRECOMPILE, methodInterface, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with isKyc function for fungible token")
    public void isKycFungibleEstimateGas() {
        final var methodInterface = getFlaggedValue(IS_KYC);
        var data =
                encodeData(PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with isKyc function for NFT")
    public void isKycNonFungibleEstimateGas() {
        final var methodInterface = getFlaggedValue(IS_KYC);
        var data = encodeData(
                PRECOMPILE, methodInterface, nonFungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with isFrozen function for fungible token")
    public void isFrozenFungibleEstimateGas() {
        final var methodInterface = getFlaggedValue(IS_FROZEN);
        var data =
                encodeData(PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with isFrozen function for NFT")
    public void isFrozenNonFungibleEstimateGas() {
        final var methodInterface = getFlaggedValue(IS_FROZEN);
        var data = encodeData(
                PRECOMPILE, methodInterface, nonFungibleKycUnfrozenTokenAddress, receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenType function for fungible token")
    public void getTokenTypeFungibleEstimateGas() {
        final var methodInterface = getFlaggedValue(GET_TOKEN_TYPE);
        var data = encodeData(PRECOMPILE, methodInterface, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with getTokenType function for NFT")
    public void getTokenTypeNonFungibleEstimateGas() {
        final var methodInterface = getFlaggedValue(GET_TOKEN_TYPE);
        var data = encodeData(PRECOMPILE, methodInterface, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect balanceOf function")
    public void redirectBalanceOfEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_BALANCE_OF, fungibleKycUnfrozenTokenAddress, adminAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_BALANCE_OF, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect name function")
    public void redirectNameEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_NAME, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_NAME, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect symbol function")
    public void redirectSymbolEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_SYMBOL, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_SYMBOL, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect name function for NFT")
    public void redirectNameNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_NAME, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_NAME, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect symbol function for NFT")
    public void redirectSymbolNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_SYMBOL, nonFungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_SYMBOL, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect decimals function")
    public void redirectDecimalsEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_DECIMALS, fungibleKycUnfrozenTokenAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_DECIMALS, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect allowance function")
    public void redirectAllowanceEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_ALLOWANCE,
                fungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_ALLOWANCE, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect getOwnerOf function")
    public void redirectGetOwnerOfEstimateGas() {
        var data = encodeData(
                PRECOMPILE, REDIRECT_FOR_TOKEN_GET_OWNER_OF, nonFungibleKycUnfrozenTokenAddress, new BigInteger("1"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_GET_OWNER_OF, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect tokenURI function")
    public void redirectTokenURIEstimateGas() {
        var data = encodeData(
                PRECOMPILE, REDIRECT_FOR_TOKEN_TOKEN_URI, nonFungibleKycUnfrozenTokenAddress, new BigInteger("1"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_TOKEN_URI, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect isApprovedForAll function")
    public void redirectIsApprovedForAllEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL,
                nonFungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL, deployedPrecompileContract);
    }

    @And("I transfer fungible token to the precompile contract")
    public void transferFungibleToPrecompileContract() {
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                admin,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()),
                receiverAccount.getPrivateKey(),
                10);
    }

    @Then("I call estimateGas with redirect transfer function")
    public void redirectTransferEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_TRANSFER,
                fungibleTokenAddress,
                receiverAccountAliasAddress,
                new BigInteger("5"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_TRANSFER, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect transferFrom function")
    public void redirectTransferFromEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_TRANSFER_FROM,
                fungibleTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("5"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_TRANSFER_FROM, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect approve function")
    public void redirectApproveEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_APPROVE,
                fungibleTokenAddress,
                receiverAccountAliasAddress,
                new BigInteger("10"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_APPROVE, deployedPrecompileContract);
    }

    @And("I approve for all nft for precompile contract")
    public void approveForAllNftForPrecompileContract() {
        var contractId = TestUtil.fromSolidityAddress(precompileTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveNftAllSerials(nonFungibleTokenId, contractId);
    }

    @Then("I call estimateGas with redirect transferFrom NFT function")
    public void redirectTransferFromNonFungibleEstimateGas() {
        final var methodInterface = getFlaggedValue(REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT);
        var data = encodeData(
                PRECOMPILE,
                methodInterface,
                nonFungibleTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("2"));

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimateGas with redirect setApprovalForAll function")
    public void redirectSetApprovalForAllEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL,
                nonFungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress,
                true);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL, deployedPrecompileContract);
    }

    @Then("I call estimateGas with pseudo random seed")
    public void pseudoRandomSeedEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PSEUDO_RANDOM_SEED);

        validateGasEstimation(data, PSEUDO_RANDOM_SEED, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with pseudo random number")
    public void pseudoRandomNumberEstimateGas() {
        final var methodInterface = getFlaggedValue(PSEUDO_RANDOM_NUMBER);
        var data = encodeData(ESTIMATE_PRECOMPILE, methodInterface, 500L, 1000L);

        validateGasEstimation(data, methodInterface, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with exchange rate tinycents to tinybars")
    public void exchangeRateTinyCentsToTinyBarsEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, EXCHANGE_RATE_TINYCENTS_TO_TINYBARS, new BigInteger("100"));

        validateGasEstimation(data, EXCHANGE_RATE_TINYCENTS_TO_TINYBARS, deployedEstimatePrecompileContract);
    }

    @Then("I call estimateGas with exchange rate tinybars to tinycents")
    public void exchangeRateTinyBarsToTinyCentsEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, EXCHANGE_RATE_TINYBARS_TO_TINYCENTS, new BigInteger("100"));

        validateGasEstimation(data, EXCHANGE_RATE_TINYBARS_TO_TINYCENTS, deployedEstimatePrecompileContract);
    }

    private void executeContractTransaction(
            DeployedContract deployedContract, int gas, ContractMethodInterface contractMethods, byte[] parameters) {

        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedContract.contractId(), gas, contractMethods.getSelector(), parameters, null);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    public int validateAndReturnGas(byte[] data, ContractMethodInterface contractMethods, String contractAddress) {
        var encodedData = Strings.encode(ByteBuffer.wrap(data));
        var response = estimateContract(encodedData, contractMethods.getActualGas(), contractAddress);
        var estimateGasValue = response.getResultAsNumber().intValue();
        assertWithinDeviation(contractMethods.getActualGas(), estimateGasValue, lowerDeviation, upperDeviation);
        return estimateGasValue;
    }

    @Then("I call estimateGas with balanceOf function for {token} and verify the estimated gas against HAPI")
    public void executeBalanceOfFunctionWithLimitedGas(TokenNameEnum tokenName) {
        final var methodInterface = getFlaggedValue(BALANCE_OF);
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeDataToByteArray(ERC, methodInterface, asAddress(tokenId), adminAddress);
        var estimateGasValue = validateAndReturnGas(data, methodInterface, ercTestContractSolidityAddress);
        executeContractTransaction(deployedErcTestContract, estimateGasValue, methodInterface, data);
    }

    @And("I update the account and token keys")
    public void updateAccountAndTokenKeys() throws PrecheckStatusException, TimeoutException, ReceiptStatusException {
        var keyList = KeyList.of(admin.getPublicKey(), deployedEstimatePrecompileContract.contractId())
                .setThreshold(1);
        new AccountUpdateTransaction()
                .setAccountId(admin.getAccountId())
                .setKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        new TokenUpdateTransaction()
                .setTokenId(fungibleTokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        var tokenUpdate = new TokenUpdateTransaction()
                .setTokenId(nonFungibleTokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        networkTransactionResponse = new NetworkTransactionResponse(
                tokenUpdate.transactionId, tokenUpdate.getReceipt(accountClient.getClient()));
    }

    @Then("I call estimateGas with transferToken function and verify the estimated gas against HAPI")
    public void executeTransferForFungibleWithGasLimit() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_TOKEN,
                fungibleTokenAddress,
                adminAddress,
                secondReceiverAccountAddress,
                5L);
        var estimateGasValue = validateAndReturnGas(data, TRANSFER_TOKEN, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, TRANSFER_TOKEN, data);
    }

    @And("I associate the contract with the receiver account")
    public void associateSecondReceiverWithContract() {
        networkTransactionResponse = tokenClient.associate(secondReceiverAccount, nonFungibleTokenId);
    }

    @Then("I call estimateGas with transferNFT function and verify the estimated gas against HAPI")
    public void executeTransferTokenNonFungibleWithGasLimit() {
        final var methodInterface = getFlaggedValue(TRANSFER_NFT);
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                nonFungibleTokenAddress,
                adminAddress,
                secondReceiverAccountAddress,
                2L);
        var estimateGasValue = validateAndReturnGas(data, methodInterface, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, methodInterface, data);
    }

    @And("I approve the receiver to use the token")
    public void approveFungibleTokensToContract() throws InvalidProtocolBufferException {
        NftId id = new NftId(nonFungibleTokenId, 3L);
        var accountId = AccountId.fromBytes(
                deployedEstimatePrecompileContract.contractId().toBytes());
        accountClient.approveNft(id, accountId);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, accountId, 10);
    }

    @Then("I call estimateGas with allowance function for fungible token and verify the estimated gas against HAPI")
    public void executeAllowanceFungibleWithLimitedGas() {
        final var methodInterface = getFlaggedValue(ALLOWANCE);
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                fungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress);
        var estimateGasValue = validateAndReturnGas(data, methodInterface, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, methodInterface, data);
    }

    @Then("I call estimateGas with allowance function for NFT and verify the estimated gas against HAPI")
    public void executeAllowanceNonFungibleWithLimitedGas() {
        final var methodInterface = getFlaggedValue(ALLOWANCE);
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                nonFungibleKycUnfrozenTokenAddress,
                adminAddress,
                receiverAccountAliasAddress);
        var estimateGasValue = validateAndReturnGas(data, methodInterface, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, methodInterface, data);
    }

    @Then("I call estimateGas with approve function and verify the estimated gas against HAPI")
    public void executeApproveWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, APPROVE, fungibleTokenAddress, receiverAccountAliasAddress, new BigInteger("10"));
        var estimateGasValue = validateAndReturnGas(data, APPROVE, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, APPROVE, data);
    }

    @Then("I call estimateGas with approveNFT function and verify the estimated gas against HAPI")
    public void executeApproveNftWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                APPROVE_NFT,
                nonFungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress,
                new BigInteger("1"));
        var estimateGasValue = validateAndReturnGas(data, APPROVE_NFT, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, APPROVE_NFT, data);
    }

    @Then("I call estimateGas with transferFrom function with fungible and verify the estimated gas against HAPI")
    public void executeTransferFromWithGasLimit() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM,
                fungibleTokenAddress,
                adminAddress,
                secondReceiverAccountAddress,
                new BigInteger("5"));
        var estimateGasValue = validateAndReturnGas(data, TRANSFER_FROM, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, TRANSFER_FROM, data);
    }

    @Then("I call estimateGas with transferFromNFT function and verify the estimated gas against HAPI")
    public void executeTransferFromNFTWithGasLimit() {
        final var methodInterface = getFlaggedValue(TRANSFER_FROM_NFT);
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                nonFungibleTokenAddress,
                adminAddress,
                secondReceiverAccountAddress,
                new BigInteger("3"));
        var estimateGasValue = validateAndReturnGas(data, methodInterface, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, methodInterface, data);
    }

    @Then("I call estimate gas that mints FUNGIBLE token and gets the total supply and balance")
    public void estimateGasMintFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        final var methodInterface = getFlaggedValue(MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE);
        var data = encodeData(PRECOMPILE, methodInterface, fungibleTokenAddress, 1L, new byte[][] {}, adminAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimate gas that mints NFT token and gets the total supply and balance")
    public void estimateGasMintNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE,
                nonFungibleTokenAddress,
                0L,
                asByteArray(List.of("0x02")),
                adminAddress);

        validateGasEstimation(data, MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE, deployedPrecompileContract);
    }

    @Then("I call estimate gas that burns FUNGIBLE token and gets the total supply and balance")
    public void estimateGasBurnFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        final var methodInterface = getFlaggedValue(BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE);
        var data =
                encodeData(PRECOMPILE, methodInterface, fungibleTokenAddress, 1L, asLongArray(List.of()), adminAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimate gas that burns NFT token and returns the total supply and balance of treasury")
    public void estimateGasBurnNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE,
                nonFungibleTokenAddress,
                0L,
                asLongArray(List.of(1L)),
                adminAddress);

        validateGasEstimation(data, BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE, deployedPrecompileContract);
    }

    @Then("I call estimate gas that wipes FUNGIBLE token and gets the total supply and balance")
    public void estimateGasWipeFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        final var methodInterface = getFlaggedValue(WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE);
        var data = encodeData(
                PRECOMPILE,
                methodInterface,
                fungibleTokenAddress,
                1L,
                asLongArray(List.of()),
                receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimate gas that wipes NFT token and gets the total supply and balance")
    public void estimateGasWipeNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        final var methodInterface = getFlaggedValue(WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE);
        var data = encodeData(
                PRECOMPILE,
                methodInterface,
                fungibleTokenAddress,
                0L,
                asLongArray(List.of(1L)),
                receiverAccountAliasAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimate gas that pauses FUNGIBLE token, unpauses and gets the token status")
    public void estimateGasPauseFungibleTokenGetStatusUnpauseGetStatus() {
        var data = encodeData(PRECOMPILE, PAUSE_UNPAUSE_GET_STATUS, fungibleTokenAddress);

        validateGasEstimation(data, PAUSE_UNPAUSE_GET_STATUS, deployedPrecompileContract);
    }

    @Then("I call estimate gas that pauses NFT token, unpauses and gets the token status")
    public void estimateGasPauseNFTTokenGetStatusUnpauseGetStatus() {
        var data = encodeData(PRECOMPILE, PAUSE_UNPAUSE_GET_STATUS, nonFungibleTokenAddress);

        validateGasEstimation(data, PAUSE_UNPAUSE_GET_STATUS, deployedPrecompileContract);
    }

    @Then("I call estimate gas that freezes FUNGIBLE token, unfreezes and gets freeze status")
    public void estimateGasFreezeFungibleTokenGetFreezeStatusUnfreezeGetFreezeStatus() {
        final var methodInterface = getFlaggedValue(FREEZE_UNFREEZE_GET_STATUS);
        var data = encodeData(PRECOMPILE, methodInterface, fungibleTokenAddress, adminAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimate gas that freezes NFT token, unfreezes and gets freeze status")
    public void estimateGasFreezeNftTokenGetFreezeStatusUnfreezeGetFreezeStatus() {
        final var methodInterface = getFlaggedValue(FREEZE_UNFREEZE_GET_STATUS);
        var data = encodeData(PRECOMPILE, methodInterface, nonFungibleTokenAddress, adminAddress);

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimate gas that approves FUNGIBLE token and gets allowance")
    public void estimateGasApproveFungibleTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_FUNGIBLE_GET_ALLOWANCE,
                fungibleTokenAddress,
                receiverAccountAliasAddress,
                new BigInteger("1"),
                new BigInteger("0"));

        validateGasEstimation(data, APPROVE_FUNGIBLE_GET_ALLOWANCE, deployedPrecompileContract);
    }

    @Then("I call estimate gas that approves NFT token and gets allowance")
    public void estimateGasApproveNFTTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_NFT_GET_ALLOWANCE,
                nonFungibleKycUnfrozenTokenAddress,
                receiverAccountAliasAddress,
                new BigInteger("0"),
                new BigInteger("1"));

        validateGasEstimation(data, APPROVE_NFT_GET_ALLOWANCE, deployedPrecompileContract);
    }

    @Then("I call estimate gas that associates FUNGIBLE token dissociates and fails token transfer")
    public void estimateGasAssociateFungibleTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER,
                fungibleTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("1"),
                new BigInteger("0"));

        validateGasEstimation(data, DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER, deployedPrecompileContract);
    }

    @Then("I call estimate gas that associates NFT token dissociates and fails token transfer")
    public void estimateGasAssociateNftTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_NFT_AND_TRANSFER,
                nonFungibleTokenAddress,
                adminAddress,
                receiverAccountAliasAddress,
                new BigInteger("0"),
                new BigInteger("1"));

        validateGasEstimation(data, DISSOCIATE_NFT_AND_TRANSFER, deployedPrecompileContract);
    }

    @Then("I call estimate gas that approves a FUNGIBLE token and transfers it")
    public void estimateGasApproveFungibleTokenTransferFromGetAllowanceGetBalance() {
        final var methodInterface = getFlaggedValue(APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER);
        var data = encodeData(
                PRECOMPILE, methodInterface, fungibleTokenAddress, receiverAccountAliasAddress, new BigInteger("1"));

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @Then("I call estimate gas that approves a NFT token and transfers it")
    public void approveNftTokenTransferFromGetAllowanceGetBalance() {
        final var methodInterface = getFlaggedValue(APPROVE_NFT_TOKEN_AND_TRANSFER_FROM);
        var data = encodeData(
                PRECOMPILE,
                methodInterface,
                nonFungibleTokenAddress,
                secondReceiverAccountAddress,
                new BigInteger("1"));

        validateGasEstimation(data, methodInterface, deployedPrecompileContract);
    }

    @And("I approve and transfer NFT tokens to the precompile contract")
    public void approveAndTransferNftToPrecompileContract() throws InvalidProtocolBufferException {
        accountClient.approveNftAllSerials(nonFungibleTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                receiverAccount,
                TestUtil.fromSolidityAddress(precompileTestContractSolidityAddress),
                List.of(1L),
                null,
                null,
                false);
    }

    @Then("I call estimateGas with mintToken function for fungible token and verify the estimated gas against HAPI")
    public void executeMintFungibleTokenWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, MINT_TOKEN, fungibleTokenAddress, 1L, asByteArray(new ArrayList<>()));
        var estimateGasValue = validateAndReturnGas(data, MINT_TOKEN, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, MINT_TOKEN, data);
    }

    @Then("I call estimateGas with mintToken function for NFT and verify the estimated gas against HAPI")
    public void executeMintNonFungibleWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, MINT_NFT, nonFungibleTokenAddress, 0L, asByteArray(List.of("0x02")));
        var estimateGasValue = validateAndReturnGas(data, MINT_NFT, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, MINT_NFT, data);
    }

    private void validateGasEstimationForCreateToken(String data, int actualGasUsed, long value) {
        var contractCallRequest = ModelBuilder.contractCallRequest(actualGasUsed)
                .data(data)
                .estimate(true)
                .from(contractClient.getClientAddress())
                .to(estimatePrecompileContractSolidityAddress);
        contractCallRequest.value(value);
        ContractCallResponse msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);
        int estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();
        assertWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation);
    }

    /**
     * Executes estimate gas for token create with current exchange rates and if this fails reties with next exchange
     * rates. The consumer accepts boolean value indicating if we should use current or next exchange rate. true =
     * current, false = next This is done in order to prevent edge cases like: System.currentTimeMillis() returns
     * timestamp that is within the current exchange rate limit, but after few ms the next exchange rate takes place.
     * After some ms when we call the create token with the outdated rates the test fails. We cannot ensure consistent
     * timing between the call getting the exchange rates and the create token call.
     */
    private void executeAndRetryWithNextExchangeRates(Consumer<Boolean> validationFunction) {
        try {
            validationFunction.accept(true);
            return;
        } catch (AssertionError e) {
            log.warn("Assertion failed for estimateGas with current exchange rates. Trying with next exchange rates.");
        } catch (Exception e) {
            log.warn(
                    "Exception occurred for estimateGas with current exchange rates. Trying with next exchange rates.");
        }
        validationFunction.accept(false);
    }

    /**
     * If the `modularizedServices` flag is set to `false`, return the passed value directly. Otherwise, map the passed
     * value to its equivalent from the `ContractMethodsModularizedServices` enum.
     *
     * @param contractMethods the non-`modularizedServices` enum value
     * @return the correct enum value in regard to the `modularizedServices` flag
     */
    private ContractMethodInterface getFlaggedValue(final ContractMethods contractMethods) {
        return !web3Properties.isModularizedServices()
                ? contractMethods
                : ContractMethodsModularizedServices.valueOf(contractMethods.name());
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements ContractMethodInterface {
        ALLOWANCE("allowanceExternal", 25399, MUTABLE),
        ALLOWANCE_ERC("allowance", 27481, VIEW),
        APPROVE("approveExternal", 729571, MUTABLE),
        APPROVE_NFT("approveNFTExternal", 729569, MUTABLE),
        APPROVE_ERC("approve", 731632, MUTABLE),
        ASSOCIATE_TOKEN("associateTokenExternal", 729374, MUTABLE),
        ASSOCIATE_TOKENS("associateTokensExternal", 730641, MUTABLE),
        BALANCE_OF("balanceOf", 27270, VIEW),
        BALANCE_OF_NFT("balanceOfIERC721", 27228, VIEW),
        BURN_TOKEN("burnTokenExternal", 40247, MUTABLE),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenPublic", 192752, PAYABLE),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES("createFungibleTokenWithCustomFeesPublic", 176628, PAYABLE),
        CREATE_NFT("createNonFungibleTokenPublic", 192472, PAYABLE),
        CREATE_NFT_WITH_CUSTOM_FEES("createNonFungibleTokenWithCustomFeesPublic", 195579, PAYABLE),
        CRYPTO_TRANSFER("cryptoTransferExternal", 47206, MUTABLE),
        CRYPTO_TRANSFER_HBARS("cryptoTransferExternal", 31819, MUTABLE),
        CRYPTO_TRANSFER_NFT("cryptoTransferExternal", 60258, MUTABLE),
        DECIMALS("decimals", 27143, VIEW),
        DELETE_TOKEN("deleteTokenExternal", 39095, MUTABLE),
        DISSOCIATE_AND_ASSOCIATE("dissociateAndAssociateTokenExternal", 1434814, MUTABLE),
        DISSOCIATE_TOKEN("dissociateTokenExternal", 729428, MUTABLE),
        DISSOCIATE_TOKENS("dissociateTokensExternal", 730641, MUTABLE),
        EXCHANGE_RATE_TINYCENTS_TO_TINYBARS("tinycentsToTinybars", 24833, MUTABLE),
        EXCHANGE_RATE_TINYBARS_TO_TINYCENTS("tinybarsToTinycents", 24811, MUTABLE),
        FREEZE_TOKEN("freezeTokenExternal", 39339, MUTABLE),
        GET_APPROVED("getApprovedExternal", 25192, MUTABLE),
        GET_APPROVED_ERC("getApproved", 27393, VIEW),
        GET_FUNGIBLE_TOKEN_INFO("getInformationForFungibleToken", 56456, VIEW),
        GET_NON_FUNGIBLE_TOKEN_INFO("getInformationForNonFungibleToken", 59159, MUTABLE),
        GET_TOKEN_DEFAULT_FREEZE_STATUS("getTokenDefaultFreeze", 25191, MUTABLE),
        GET_TOKEN_DEFAULT_KYC_STATUS("getTokenDefaultKyc", 25267, MUTABLE),
        GET_TOKEN_EXPIRY_INFO("getTokenExpiryInfoExternal", 25617, MUTABLE),
        GET_TOKEN_INFO("getInformationForToken", 55815, MUTABLE),
        GET_TOKEN_INFO_NFT("getInformationForToken", 56523, MUTABLE),
        GET_TOKEN_KEY("getTokenKeyExternal", 27024, MUTABLE),
        GET_TOKEN_TYPE("getType", 25223, MUTABLE),
        GRANT_KYC("grantTokenKycExternal", 39311, MUTABLE),
        IS_APPROVED_FOR_ALL("isApprovedForAllExternal", 25483, MUTABLE),
        IS_APPROVED_FOR_ALL_ERC("isApprovedForAll", 27520, VIEW),
        IS_TOKEN("isTokenExternal", 25100, MUTABLE),
        IS_FROZEN("isTokenFrozen", 25473, MUTABLE),
        IS_KYC("isKycGranted", 25417, MUTABLE),
        MINT_TOKEN("mintTokenExternal", 40700, MUTABLE),
        MINT_NFT("mintTokenExternal", 309748, MUTABLE),
        NAME("name", 27976, VIEW),
        NAME_NFT("nameIERC721", 27837, VIEW),
        NESTED_ASSOCIATE("nestedAssociateTokenExternal", 0, MUTABLE),
        NESTED_FREEZE_UNFREEZE("nestedFreezeUnfreezeTokenExternal", 54548, MUTABLE),
        NESTED_GRANT_REVOKE_KYC("nestedGrantAndRevokeTokenKYCExternal", 54516, MUTABLE),
        OWNER_OF("getOwnerOf", 27271, VIEW),
        PSEUDO_RANDOM_SEED("getPseudorandomSeed", 36270, MUTABLE),
        PSEUDO_RANDOM_NUMBER("getPseudorandomNumber", 36729, MUTABLE),
        REVOKE_KYC("revokeTokenKycExternal", 39324, MUTABLE),
        REDIRECT_FOR_TOKEN_ALLOWANCE("allowanceRedirect", 33182, MUTABLE),
        REDIRECT_FOR_TOKEN_APPROVE("approveRedirect", 737257, MUTABLE),
        REDIRECT_FOR_TOKEN_BALANCE_OF("balanceOfRedirect", 32806, MUTABLE),
        REDIRECT_FOR_TOKEN_DECIMALS("decimalsRedirect", 32411, MUTABLE),
        REDIRECT_FOR_TOKEN_GET_APPROVED("getApprovedRedirect", 23422, MUTABLE),
        REDIRECT_FOR_TOKEN_GET_OWNER_OF("getOwnerOfRedirect", 32728, MUTABLE),
        REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL("isApprovedForAllRedirect", 33204, MUTABLE),
        REDIRECT_FOR_TOKEN_NAME("nameNFTRedirect", 33615, MUTABLE),
        REDIRECT_FOR_TOKEN_SYMBOL("symbolNFTRedirect", 33681, MUTABLE),
        REDIRECT_FOR_TOKEN_NAME_NFT("nameRedirect", 23422, MUTABLE),
        REDIRECT_FOR_TOKEN_SYMBOL_NFT("symbolRedirect", 23422, MUTABLE),
        REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL("setApprovalForAllRedirect", 737243, MUTABLE),
        REDIRECT_FOR_TOKEN_TOTAL_SUPPLY("totalSupplyRedirect", 23422, MUTABLE),
        REDIRECT_FOR_TOKEN_TOKEN_URI("tokenURIRedirect", 33997, MUTABLE),
        REDIRECT_FOR_TOKEN_TRANSFER("transferRedirect", 47842, MUTABLE),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM("transferFromRedirect", 48274, MUTABLE),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT("transferFromNFTRedirect", 62336, MUTABLE),
        SET_APPROVAL_FOR_ALL("setApprovalForAllExternal", 729608, MUTABLE),
        SYMBOL("symbol", 27815, VIEW),
        SYMBOL_NFT("symbolIERC721", 27814, VIEW),
        TOTAL_SUPPLY("totalSupply", 27100, VIEW),
        TOTAL_SUPPLY_NFT("totalSupplyIERC721", 27078, VIEW),
        TOKEN_URI("tokenURI", 27856, VIEW),
        TRANSFER_ERC("transfer", 42138, MUTABLE),
        TRANSFER_FROM("transferFromExternal", 41307, MUTABLE),
        TRANSFER_FROM_ERC("transferFrom", 42475, MUTABLE),
        TRANSFER_FROM_NFT("transferFromNFTExternal", 55478, MUTABLE),
        TRANSFER_NFT("transferNFTExternal", 54596, MUTABLE),
        TRANSFER_NFTS("transferNFTsExternal", 58999, MUTABLE),
        TRANSFER_TOKEN("transferTokenExternal", 39666, MUTABLE),
        TRANSFER_TOKENS("transferTokensExternal", 48326, MUTABLE),
        UNFREEZE_TOKEN("unfreezeTokenExternal", 39323, MUTABLE),
        WIPE_TOKEN_ACCOUNT("wipeTokenAccountExternal", 39496, MUTABLE),
        WIPE_NFT_ACCOUNT("wipeTokenAccountNFTExternal", 40394, MUTABLE),
        PAUSE_TOKEN("pauseTokenExternal", 39112, MUTABLE),
        PAUSE_UNPAUSE_NESTED_TOKEN("nestedPauseUnpauseTokenExternal", 54237, MUTABLE),
        UNPAUSE_TOKEN("unpauseTokenExternal", 39112, MUTABLE),
        UPDATE_TOKEN_EXPIRY("updateTokenExpiryInfoExternal", 39699, MUTABLE),
        UPDATE_TOKEN_INFO("updateTokenInfoExternal", 74920, MUTABLE),
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", 60427, MUTABLE),
        MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 68127, MUTABLE),
        MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 335855, MUTABLE),
        BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 66908, MUTABLE),
        BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 66886, MUTABLE),
        WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 88477, MUTABLE),
        WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 88970, MUTABLE),
        PAUSE_UNPAUSE_GET_STATUS("pauseTokenGetPauseStatusUnpauseGetPauseStatus", 98345, MUTABLE),
        FREEZE_UNFREEZE_GET_STATUS("freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus", 57387, MUTABLE),
        APPROVE_FUNGIBLE_GET_ALLOWANCE("approveTokenGetAllowance", 733080, MUTABLE),
        APPROVE_NFT_GET_ALLOWANCE("approveTokenGetAllowance", 733127, MUTABLE),
        DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER("associateTokenDissociateFailTransfer", 1482987, MUTABLE),
        DISSOCIATE_NFT_AND_TRANSFER("associateTokenDissociateFailTransfer", 1525177, MUTABLE),
        APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER("approveFungibleTokenTransferFromGetAllowanceGetBalance", 840000, MUTABLE),
        APPROVE_NFT_TOKEN_AND_TRANSFER_FROM("approveNftAndTransfer", 835000, MUTABLE);

        private final String selector;
        private final int actualGas;
        private final FunctionType functionType;
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethodsModularizedServices implements ContractMethodInterface {
        TRANSFER_NFT("transferNFTExternal", 41571, MUTABLE),
        ASSOCIATE_TOKENS("associateTokensExternal", 1336847, MUTABLE),
        CRYPTO_TRANSFER_NFT("cryptoTransferExternal", 47372, MUTABLE),
        GET_TOKEN_EXPIRY_INFO("getTokenExpiryInfoExternal", 28607, MUTABLE),
        IS_TOKEN("isTokenExternal", 27977, MUTABLE),
        GET_TOKEN_KEY("getTokenKeyExternal", 30778, MUTABLE),
        ALLOWANCE_ERC("allowance", 30724, VIEW),
        GET_APPROVED("getApprovedExternal", 28200, MUTABLE),
        GET_APPROVED_ERC("getApproved", 30256, VIEW),
        IS_APPROVED_FOR_ALL("isApprovedForAllExternal", 28860, MUTABLE),
        IS_APPROVED_FOR_ALL_ERC("isApprovedForAll", 30763, VIEW),
        NAME_NFT("nameIERC721", 30764, VIEW),
        SYMBOL("symbol", 30742, VIEW),
        SYMBOL_NFT("symbolIERC721", 30741, VIEW),
        DECIMALS("decimals", 29998, VIEW),
        TOTAL_SUPPLY("totalSupply", 29955, VIEW),
        TOTAL_SUPPLY_NFT("totalSupplyIERC721", 29933, VIEW),
        OWNER_OF("getOwnerOf", 30266, VIEW),
        TOKEN_URI("tokenURI", 30923, VIEW),
        GET_TOKEN_DEFAULT_FREEZE_STATUS("getTokenDefaultFreeze", 27980, MUTABLE),
        GET_TOKEN_DEFAULT_KYC_STATUS("getTokenDefaultKyc", 28011, MUTABLE),
        IS_KYC("isKycGranted", 28542, MUTABLE),
        IS_FROZEN("isTokenFrozen", 28575, MUTABLE),
        GET_TOKEN_TYPE("getType", 27991, MUTABLE),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT("transferFromNFTRedirect", 47116, MUTABLE),
        MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 76875, MUTABLE),
        BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 76413, MUTABLE),
        WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 101170, MUTABLE),
        WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 101792, MUTABLE),
        FREEZE_UNFREEZE_GET_STATUS("freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus", 65626, MUTABLE),
        PSEUDO_RANDOM_NUMBER("getPseudorandomNumber", 40666, MUTABLE),
        TRANSFER_FROM_NFT("transferFromNFTExternal", 42745, MUTABLE),
        BALANCE_OF("balanceOf", 30277, VIEW),
        ALLOWANCE("allowanceExternal", 28778, MUTABLE),
        APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER("approveFungibleTokenTransferFromGetAllowanceGetBalance", 840000, MUTABLE),
        APPROVE_NFT_TOKEN_AND_TRANSFER_FROM("approveNftAndTransfer", 830000, MUTABLE);

        private final String selector;
        private final int actualGas;
        private final FunctionType functionType;
    }
}
