// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_KYC_UNFROZEN;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT;
import static org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT_KYC_UNFROZEN;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
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
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.TokenTransferListBuilder;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.accountAmount;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asByteArray;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asLongArray;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.nftAmount;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import org.hiero.mirror.test.e2e.acceptance.config.Web3Properties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;

@CustomLog
@RequiredArgsConstructor
public class EstimatePrecompileFeature extends AbstractEstimateFeature {
    private static final Tuple[] EMPTY_TUPLE_ARRAY = new Tuple[] {};
    private static final long FIRST_NFT_SERIAL_NUMBER = 1;
    private static final long NUM_MAX_SIZE = 274877906943L;
    private final CommonProperties commonProperties;
    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private final Web3Properties web3Properties;
    private TokenId fungibleKycUnfrozenTokenId;
    private TokenId nonFungibleKycUnfrozenTokenId;

    private TokenId fungibleTokenId;
    private TokenId nonFungibleTokenId;
    private DeployedContract deployedEstimatePrecompileContract;
    private ContractId estimatePrecompileContractId;
    private DeployedContract deployedErcTestContract;
    private DeployedContract deployedPrecompileContract;
    private ExpandedAccountId receiverAccount;
    private String receiverAccountAlias;
    private AccountId senderAccountId;
    private ExpandedAccountId secondReceiverAccount;
    private ExpandedAccountId admin;
    private String ercTestContractSolidityAddress;
    private ContractId ercTestContractId;
    private String precompileTestContractSolidityAddress;
    private ContractId precompileContractId;
    private String fungibleTokenAddressString;
    private String nonFungibleTokenAddressString;
    private String fungibleKycUnfrozenTokenIdAddressString;
    private String adminAddressString;
    private String nonFungibleKycUnfrozenAddressString;
    private String secondReceiverAddressString;

    @Given("I create estimate precompile contract with 0 balance")
    public void createNewEstimateContract() {
        deployedEstimatePrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        estimatePrecompileContractId = deployedEstimatePrecompileContract.contractId();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        adminAddressString = asAddress(admin).toString();
        receiverAccount = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        secondReceiverAccount = accountClient.getAccount(AccountNameEnum.DAVE);
        secondReceiverAddressString = asAddress(secondReceiverAccount).toString();
        receiverAccountAlias = receiverAccount.getPublicKey().toEvmAddress().toString();
        senderAccountId =
                accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId();
    }

    @Given("I create erc test contract with 0 balance")
    public void createNewERCContract() {
        deployedErcTestContract = getContract(ERC);
        ercTestContractSolidityAddress = deployedErcTestContract.contractId().toEvmAddress();
        ercTestContractId = deployedErcTestContract.contractId();
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
        precompileContractId = deployedPrecompileContract.contractId();
    }

    @Given("I successfully create fungible tokens")
    public void createFungibleToken() {
        fungibleKycUnfrozenTokenId = tokenClient.getToken(FUNGIBLE_KYC_UNFROZEN).tokenId();
        fungibleKycUnfrozenTokenIdAddressString =
                asAddress(fungibleKycUnfrozenTokenId).toString();
        fungibleTokenId = tokenClient.getToken(FUNGIBLE).tokenId();
        fungibleTokenAddressString = asAddress(fungibleTokenId).toString();
    }

    @Given("I successfully create non fungible tokens")
    public void createNonFungibleToken() {
        nonFungibleKycUnfrozenTokenId = tokenClient.getToken(NFT_KYC_UNFROZEN).tokenId();
        nonFungibleKycUnfrozenAddressString =
                asAddress(nonFungibleKycUnfrozenTokenId).toString();
        nonFungibleTokenId = tokenClient.getToken(NFT).tokenId();
        nonFungibleTokenAddressString = asAddress(nonFungibleTokenId).toString();
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

    @And("I set lower deviation at {int}% and upper deviation at {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    @Then("I call estimateGas with associate function for fungible token")
    public void associateFunctionEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(fungibleTokenAddressString);
        validateGasEstimation(
                estimatePrecompileContractId,
                ASSOCIATE_TOKEN.selector,
                parameters,
                senderAccountId,
                ASSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with associate function for NFT")
    public void associateFunctionNFTEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(nonFungibleTokenAddressString);
        validateGasEstimation(
                estimatePrecompileContractId,
                ASSOCIATE_TOKEN.selector,
                parameters,
                senderAccountId,
                ASSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate token function without association for fungible token")
    public void dissociateFunctionEstimateGasNegative() throws ExecutionException, InterruptedException {
        // attempt to call dissociate function without having association
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(fungibleTokenAddressString);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        DISSOCIATE_TOKEN.getSelector(),
                        parameters,
                        senderAccountId,
                        DISSOCIATE_TOKEN.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with dissociate token function without association for NFT")
    public void dissociateFunctionNFTEstimateGasNegative() {
        // attempt to call dissociate function without having association
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(nonFungibleTokenAddressString);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        DISSOCIATE_TOKEN.getSelector(),
                        parameters,
                        senderAccountId,
                        DISSOCIATE_TOKEN.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with nested associate function that executes it twice for fungible token")
    public void nestedAssociateFunctionEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(fungibleTokenAddressString);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        NESTED_ASSOCIATE.getSelector(),
                        parameters,
                        senderAccountId,
                        NESTED_ASSOCIATE.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with nested associate function that executes it twice for NFT")
    public void nestedAssociateFunctionNFTEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(nonFungibleTokenAddressString);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        NESTED_ASSOCIATE.getSelector(),
                        parameters,
                        senderAccountId,
                        NESTED_ASSOCIATE.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @And("I associate the receiver account with the fungible token")
    public void associateReceiverWithFungibleEstimateGas() {
        // associating the token with the token address
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for fungible token")
    public void dissociateFunctionEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(fungibleTokenAddressString);
        validateGasEstimation(
                estimatePrecompileContractId,
                DISSOCIATE_TOKEN.selector,
                parameters,
                senderAccountId,
                DISSOCIATE_TOKEN.getActualGas());
    }

    @And("I associate the receiver account with the NFT")
    public void associateReceiverWithNonFungibleEstimateGas() {
        // associating the NFT with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for NFT")
    public void dissociateFunctionNFTEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(nonFungibleTokenAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                DISSOCIATE_TOKEN.selector,
                parameters,
                senderAccountId,
                DISSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate and associate nested function for fungible token")
    public void dissociateAndAssociatedEstimateGas() throws ExecutionException, InterruptedException {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(fungibleTokenAddressString);
        validateGasEstimation(
                estimatePrecompileContractId,
                DISSOCIATE_AND_ASSOCIATE.selector,
                parameters,
                senderAccountId,
                DISSOCIATE_AND_ASSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with dissociate and associate nested function for NFT")
    public void dissociateAndAssociatedNFTEstimateGas() throws ExecutionException, InterruptedException {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddress(nonFungibleTokenAddressString);
        validateGasEstimation(
                estimatePrecompileContractId,
                DISSOCIATE_AND_ASSOCIATE.selector,
                parameters,
                senderAccountId,
                DISSOCIATE_AND_ASSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with approve function without association")
    public void approveWithoutAssociationEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.TEN);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        APPROVE.getSelector(),
                        parameters,
                        senderAccountId,
                        APPROVE.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with setApprovalForAll function without association")
    public void setApprovalForAllWithoutAssociationEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addBool(true);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        SET_APPROVAL_FOR_ALL.getSelector(),
                        parameters,
                        senderAccountId,
                        SET_APPROVAL_FOR_ALL.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with approveNFT function without association")
    public void approveNonFungibleWithoutAssociationEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.ONE);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        APPROVE_NFT.getSelector(),
                        parameters,
                        senderAccountId,
                        APPROVE_NFT.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
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
    public void ercApproveEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.TEN);

        validateGasEstimation(
                ercTestContractId, APPROVE_ERC.selector, parameters, senderAccountId, APPROVE_ERC.getActualGas());
    }

    @Then("I call estimateGas with setApprovalForAll function")
    public void setApprovalForAllEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias)
                .addBool(true);
        validateGasEstimation(
                estimatePrecompileContractId,
                SET_APPROVAL_FOR_ALL.selector,
                parameters,
                senderAccountId,
                SET_APPROVAL_FOR_ALL.getActualGas());
    }

    @Then("I call estimateGas with transferFrom function without approval")
    public void transferFromEstimateGasWithoutApproval() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("5"));

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        TRANSFER_FROM.getSelector(),
                        parameters,
                        senderAccountId,
                        TRANSFER_FROM.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with ERC transferFrom function without approval")
    public void ercTransferFromEstimateGasWithoutApproval() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.TEN);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        ercTestContractId,
                        TRANSFER_FROM_ERC.getSelector(),
                        parameters,
                        senderAccountId,
                        TRANSFER_FROM_ERC.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @And("I approve the contract to use fungible token")
    public void approveFungibleWithReceiver() {
        final var ercTestContractId = TestUtil.fromSolidityAddress(ercTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, ercTestContractId, 10);
        final var precompileTestContractId = TestUtil.fromSolidityAddress(precompileTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, precompileTestContractId, 10);
    }

    @Then("I call estimateGas with ERC transferFrom function")
    public void ercTransferFromEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("5"));
        validateGasEstimation(
                ercTestContractId,
                TRANSFER_FROM_ERC.selector,
                parameters,
                senderAccountId,
                TRANSFER_FROM_ERC.getActualGas());
    }

    @Then("I call estimateGas with transferFrom function with more than the approved allowance")
    public void transferFromExceedAllowanceEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("500"));

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        TRANSFER_FROM.getSelector(),
                        parameters,
                        senderAccountId,
                        TRANSFER_FROM.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with ERC transferFrom function with more than the approved allowance")
    public void ercTransferFromExceedsAllowanceEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("500"));

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        ercTestContractId,
                        TRANSFER_FROM_ERC.getSelector(),
                        parameters,
                        senderAccountId,
                        TRANSFER_FROM_ERC.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @And("I approve receiver account to use the NFT with id 1")
    public void approveNonFungibleWithReceiver() {
        var id = new NftId(nonFungibleTokenId, FIRST_NFT_SERIAL_NUMBER);
        networkTransactionResponse = accountClient.approveNft(id, receiverAccount.getAccountId());
    }

    @Then("I call estimateGas with transferFromNFT with invalid serial number")
    public void transferFromNFTInvalidSerialEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("50"));

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        TRANSFER_FROM_NFT.getSelector(),
                        parameters,
                        senderAccountId,
                        TRANSFER_FROM_NFT.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with transferNFT function")
    public void transferNFTEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(TRANSFER_NFT);
        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addInt64(1L);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
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
    public void ercTransferEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("5"));

        validateGasEstimation(
                ercTestContractId,
                TRANSFER_ERC.getSelector(),
                parameters,
                senderAccountId,
                TRANSFER_ERC.getActualGas());
    }

    @Then("I call estimateGas with associateTokens function for fungible tokens")
    public void associateTokensEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(ASSOCIATE_TOKENS);
        final var parameters = new ContractFunctionParameters()
                .addAddress(secondReceiverAddressString)
                .addAddressArray(new String[] {fungibleTokenAddressString, fungibleKycUnfrozenTokenIdAddressString});

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with associateTokens function for NFTs")
    public void associateNFTEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(ASSOCIATE_TOKENS);
        final var parameters = new ContractFunctionParameters()
                .addAddress(secondReceiverAddressString)
                .addAddressArray(new String[] {nonFungibleKycUnfrozenAddressString, nonFungibleTokenAddressString});
        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @And("I associate the fungible_kyc_unfrozen token with the receiver account")
    public void associateFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for fungible tokens")
    public void dissociateTokensEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddressArray(new String[] {fungibleTokenAddressString, fungibleKycUnfrozenTokenIdAddressString});

        validateGasEstimation(
                estimatePrecompileContractId,
                DISSOCIATE_TOKENS.getSelector(),
                parameters,
                senderAccountId,
                DISSOCIATE_TOKENS.getActualGas());
    }

    @And("I associate the nft_kyc_unfrozen with the receiver account")
    public void associateNonFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for NFTs")
    public void dissociateNFTEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(receiverAccountAlias)
                .addAddressArray(new String[] {nonFungibleKycUnfrozenAddressString, nonFungibleTokenAddressString});

        validateGasEstimation(
                estimatePrecompileContractId,
                DISSOCIATE_TOKENS.getSelector(),
                parameters,
                senderAccountId,
                DISSOCIATE_TOKENS.getActualGas());
    }

    @And("I associate and approve the second receiver to use the fungible_kyc_unfrozen token")
    public void associateAndApproveFungibleKycUnfrozenTokenWithReceiverAccount() {
        tokenClient.associate(secondReceiverAccount, fungibleTokenId);
        networkTransactionResponse =
                accountClient.approveToken(fungibleTokenId, secondReceiverAccount.getAccountId(), 10);
    }

    @Then("I call estimateGas with transferTokens function")
    public void transferTokensEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddressArray(new String[] {adminAddressString, receiverAccountAlias, secondReceiverAddressString})
                .addInt64Array(new long[] {-6L, 3L, 3L});

        validateGasEstimation(
                estimatePrecompileContractId,
                TRANSFER_TOKENS.getSelector(),
                parameters,
                senderAccountId,
                TRANSFER_TOKENS.getActualGas());
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
    public void transferNFTsEstimateGas() throws ExecutionException, InterruptedException {
        // In the modularized scenario the number of senders needs to correspond to the number of receivers.
        final var sendersList = web3Properties.isModularizedServices()
                ? new String[] {adminAddressString, adminAddressString}
                : new String[] {adminAddressString};

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddressArray(sendersList)
                .addAddressArray(new String[] {receiverAccountAlias, secondReceiverAddressString})
                .addInt64Array(new long[] {1, 2});

        validateGasEstimation(
                estimatePrecompileContractId,
                TRANSFER_NFTS.getSelector(),
                parameters,
                senderAccountId,
                TRANSFER_NFTS.getActualGas());
    }

    @Then("I call estimateGas with cryptoTransfer function for hbars")
    public void cryptoTransferHbarEstimateGas() throws ExecutionException, InterruptedException {
        var senderTransfer = accountAmount(adminAddressString, -10L, false);
        var receiverTransfer = accountAmount(receiverAccountAlias, 10L, false);
        var args = Tuple.from((Object) new Tuple[] {senderTransfer, receiverTransfer});

        var dataByteArray = encodeDataToByteArray(ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER_HBARS, args, EMPTY_TUPLE_ARRAY);

        var estimateGasResult = mirrorClient.estimateGasQueryRawData(
                estimatePrecompileContractId,
                ByteString.copyFrom(dataByteArray),
                senderAccountId,
                CRYPTO_TRANSFER_HBARS.getActualGas());

        assertWithinDeviation(
                CRYPTO_TRANSFER_HBARS.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);
    }

    private TokenTransferListBuilder tokenTransferList() {
        return new TokenTransferListBuilder();
    }

    @Then("I call estimateGas with cryptoTransfer function for nft")
    public void cryptoTransferNFTEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(CRYPTO_TRANSFER_NFT);
        var tokenTransferList = (Object) new Tuple[] {
            tokenTransferList()
                    .forToken(nonFungibleTokenAddressString)
                    .withNftTransfers(nftAmount(adminAddressString, receiverAccountAlias, 1L, false))
                    .build()
        };
        var dataByteArray = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, methodInterface, Tuple.from((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);

        var estimateGasResult = mirrorClient.estimateGasQueryRawData(
                estimatePrecompileContractId,
                ByteString.copyFrom(dataByteArray),
                senderAccountId,
                methodInterface.getActualGas());

        assertWithinDeviation(methodInterface.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);
    }

    @Then("I call estimateGas with cryptoTransfer function for fungible tokens")
    public void cryptoTransferFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var tokenTransferList = (Object) new Tuple[] {
            tokenTransferList()
                    .forToken(fungibleTokenAddressString)
                    .withAccountAmounts(
                            accountAmount(adminAddressString, -3L, false),
                            accountAmount(secondReceiverAddressString, 3L, false))
                    .build()
        };
        var dataByteArray = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER, Tuple.from((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);

        var estimateGasResult = mirrorClient.estimateGasQueryRawData(
                estimatePrecompileContractId,
                ByteString.copyFrom(dataByteArray),
                senderAccountId,
                CRYPTO_TRANSFER.getActualGas());

        assertWithinDeviation(CRYPTO_TRANSFER.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);
    }

    @Then("I call estimateGas with burnToken function for fungible token")
    public void burnFungibleTokenEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addInt64(1L)
                .addInt64Array(asLongArray(new ArrayList<>()));

        validateGasEstimation(
                estimatePrecompileContractId,
                BURN_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                BURN_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with burnToken function for NFT")
    public void burnNFTEstimateGas() throws ExecutionException, InterruptedException {
        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addInt64(0L)
                .addInt64Array(asLongArray(List.of(1L)));

        validateGasEstimation(
                estimatePrecompileContractId,
                BURN_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                BURN_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with CreateFungibleToken function")
    public void createFungibleTokenEstimateGas() {
        final var parameters = new ContractFunctionParameters().addAddress(adminAddressString);

        Consumer<Boolean> estimateFunction = current -> {
            try {
                validateGasEstimation(
                        estimatePrecompileContractId,
                        CREATE_FUNGIBLE_TOKEN.getSelector(),
                        parameters,
                        senderAccountId,
                        CREATE_FUNGIBLE_TOKEN.getActualGas(),
                        Optional.of(calculateCreateTokenFee(1, current)));
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function")
    public void createNFTEstimateGas() {
        var parameters = new ContractFunctionParameters().addAddress(adminAddressString);

        Consumer<Boolean> estimateFunction = current -> {
            try {
                validateGasEstimation(
                        estimatePrecompileContractId,
                        CREATE_NFT.getSelector(),
                        parameters,
                        senderAccountId,
                        CREATE_NFT.getActualGas(),
                        Optional.of(calculateCreateTokenFee(1, current)));
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateFungibleToken function with custom fees")
    public void createFungibleTokenWithCustomFeesEstimateGas() {
        var parameters = new ContractFunctionParameters()
                .addAddress(adminAddressString)
                .addAddress(fungibleKycUnfrozenTokenIdAddressString);

        Consumer<Boolean> estimateFunction = current -> {
            try {
                validateGasEstimation(
                        estimatePrecompileContractId,
                        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getSelector(),
                        parameters,
                        senderAccountId,
                        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas(),
                        Optional.of(calculateCreateTokenFee(2, current)));
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function with custom fees")
    public void createNFTWithCustomFeesEstimateGas() {
        var parameters =
                new ContractFunctionParameters().addAddress(adminAddressString).addAddress(fungibleTokenAddressString);

        Consumer<Boolean> estimateFunction = current -> {
            try {
                validateGasEstimation(
                        estimatePrecompileContractId,
                        CREATE_NFT_WITH_CUSTOM_FEES.getSelector(),
                        parameters,
                        senderAccountId,
                        CREATE_NFT_WITH_CUSTOM_FEES.getActualGas(),
                        Optional.of(calculateCreateTokenFee(2, current)));
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @And("I approve and transfer fungible tokens to receiver account")
    public void approveAndTransferFungibleTokensToReceiverAccount() {
        accountClient.approveToken(fungibleTokenId, receiverAccount.getAccountId(), 100L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId, admin, receiverAccount.getAccountId(), receiverAccount.getPrivateKey(), 10L);
    }

    @Then("I call estimateGas with WipeTokenAccount function")
    public void wipeTokenAccountEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addInt64(1L);

        validateGasEstimation(
                estimatePrecompileContractId,
                WIPE_TOKEN_ACCOUNT.getSelector(),
                parameters,
                senderAccountId,
                WIPE_TOKEN_ACCOUNT.getActualGas());
    }

    @Then("I call estimateGas with WipeTokenAccount function with invalid amount")
    public void wipeTokenAccountInvalidAmountEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias)
                .addInt64(100000000000000000L);

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        WIPE_TOKEN_ACCOUNT.getSelector(),
                        parameters,
                        senderAccountId,
                        WIPE_TOKEN_ACCOUNT.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
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
    public void wipeNFTAccountEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addInt64Array(asLongArray(List.of(1L)));

        validateGasEstimation(
                estimatePrecompileContractId,
                WIPE_NFT_ACCOUNT.getSelector(),
                parameters,
                senderAccountId,
                WIPE_NFT_ACCOUNT.getActualGas());
    }

    @Then("I call estimateGas with WipeNFTAccount function with invalid serial number")
    public void wipeNFTAccountInvalidSerialNumberEstimateGas() {
        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias)
                .addInt64Array(asLongArray(List.of(66L)));

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        WIPE_NFT_ACCOUNT.getSelector(),
                        parameters,
                        senderAccountId,
                        WIPE_NFT_ACCOUNT.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with GrantKYC function for fungible token")
    public void grantKYCFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                GRANT_KYC.getSelector(),
                parameters,
                senderAccountId,
                GRANT_KYC.getActualGas());
    }

    @Then("I call estimateGas with GrantKYC function for NFT")
    public void grantKYCNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                GRANT_KYC.getSelector(),
                parameters,
                senderAccountId,
                GRANT_KYC.getActualGas());
    }

    @Then("I call estimateGas with RevokeTokenKYC function for fungible token")
    public void revokeTokenKYCEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                REVOKE_KYC.getSelector(),
                parameters,
                senderAccountId,
                REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with RevokeTokenKYC function for NFT")
    public void revokeTokenKYCNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                REVOKE_KYC.getSelector(),
                parameters,
                senderAccountId,
                REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with Grant and Revoke KYC nested function")
    public void nestedGrantRevokeKYCEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                NESTED_GRANT_REVOKE_KYC.getSelector(),
                parameters,
                senderAccountId,
                NESTED_GRANT_REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with Freeze function for fungible token")
    public void freezeFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                FREEZE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                FREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Freeze function for NFT")
    public void freezeNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                FREEZE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                FREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Unfreeze function for fungible token")
    public void unfreezeFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                UNFREEZE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                UNFREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Unfreeze function for NFT")
    public void unfreezeNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                UNFREEZE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                UNFREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for fungible token")
    public void nestedFreezeAndUnfreezeEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                NESTED_FREEZE_UNFREEZE.getSelector(),
                parameters,
                senderAccountId,
                NESTED_FREEZE_UNFREEZE.getActualGas());
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for NFT")
    public void nestedFreezeAndUnfreezeNFTEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                NESTED_FREEZE_UNFREEZE.getSelector(),
                parameters,
                senderAccountId,
                NESTED_FREEZE_UNFREEZE.getActualGas());
    }

    @Then("I call estimateGas with delete function for Fungible token")
    public void deleteFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                DELETE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                DELETE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with delete function for NFT")
    public void deleteNFTEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                DELETE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                DELETE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with delete function for invalid token address")
    public void deleteTokenInvalidAddressEstimateGas() {
        String address = Hex.toHexString(ByteBuffer.allocate(20)
                .putInt((int) commonProperties.getShard())
                .putLong(commonProperties.getRealm())
                .putLong(new SecureRandom().nextLong(NUM_MAX_SIZE / 100, NUM_MAX_SIZE))
                .array());

        final var parameters =
                new ContractFunctionParameters().addAddress(asAddress(address).toString());

        assertThatThrownBy(() -> mirrorClient.estimateGasQueryTopLevelCall(
                        estimatePrecompileContractId,
                        DELETE_TOKEN.getSelector(),
                        parameters,
                        senderAccountId,
                        DELETE_TOKEN.getActualGas(),
                        Optional.empty()))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.toString());
    }

    @Then("I call estimateGas with pause function for fungible token")
    public void pauseFungibleTokenPositiveEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                PAUSE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                PAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with pause function for NFT")
    public void pauseNFTPositiveEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                PAUSE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                PAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with unpause function for fungible token")
    public void unpauseFungibleTokenPositiveEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                UNPAUSE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                UNPAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with unpause function for NFT")
    public void unpauseNFTPositiveEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                UNPAUSE_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                UNPAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas for nested pause and unpause function")
    public void pauseUnpauseFungibleTokenNestedCallEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                PAUSE_UNPAUSE_NESTED_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas());
    }

    @Then("I call estimateGas for nested pause, unpause NFT function")
    public void pauseUnpauseNFTNestedCallEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                PAUSE_UNPAUSE_NESTED_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with updateTokenExpiryInfo function")
    public void updateTokenExpiryInfoEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                UPDATE_TOKEN_EXPIRY.getSelector(),
                parameters,
                senderAccountId,
                UPDATE_TOKEN_EXPIRY.getActualGas());
    }

    @Then("I call estimateGas with updateTokenInfo function")
    public void updateTokenInfoEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                UPDATE_TOKEN_INFO.getSelector(),
                parameters,
                senderAccountId,
                UPDATE_TOKEN_INFO.getActualGas());
    }

    @Then("I call estimateGas with updateTokenKeys function")
    public void updateTokenKeysEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                UPDATE_TOKEN_KEYS.getSelector(),
                parameters,
                senderAccountId,
                UPDATE_TOKEN_KEYS.getActualGas());
    }

    @Then("I call estimateGas with getTokenExpiryInfo function")
    public void getTokenExpiryInfoEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_EXPIRY_INFO);

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with isToken function")
    public void isTokenEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(IS_TOKEN);
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenKey function for supply")
    public void getTokenKeySupplyEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addUint256(new BigInteger("16"));

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenKey function for KYC")
    public void getTokenKeyKYCEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addUint256(BigInteger.TWO);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenKey function for freeze")
    public void getTokenKeyFreezeEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addUint256(new BigInteger("4"));

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenKey function for admin")
    public void getTokenKeyAdminEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenKey function for wipe")
    public void getTokenKeyWipeEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addUint256(new BigInteger("8"));

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenKey function for fee")
    public void getTokenKeyFeeEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addUint256(new BigInteger("32"));

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenKey function for pause")
    public void getTokenKeyPauseEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_KEY);
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addUint256(new BigInteger("64"));

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with ERC allowance function for fungible token")
    public void ercAllowanceFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(ALLOWANCE_ERC);
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getApproved function for NFT")
    public void getApprovedNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_APPROVED);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with ERC getApproved function for NFT")
    public void ercGetApprovedNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_APPROVED_ERC);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with isApprovedForAll function")
    public void isApprovedForAllEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(IS_APPROVED_FOR_ALL);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with ERC isApprovedForAll function")
    public void ercIsApprovedForAllEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(IS_APPROVED_FOR_ALL_ERC);
        // reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with name function for fungible token")
    public void nameEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(ercTestContractId, NAME.getSelector(), parameters, senderAccountId, NAME.getActualGas());
    }

    @Then("I call estimateGas with name function for NFT")
    public void nameNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(NAME_NFT);

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with symbol function for fungible token")
    public void symbolEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(SYMBOL);

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with symbol function for NFT")
    public void symbolNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(SYMBOL_NFT);

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with decimals function for fungible token")
    public void decimalsEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(DECIMALS);

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with totalSupply function for fungible token")
    public void totalSupplyEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(TOTAL_SUPPLY);

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with totalSupply function for NFT")
    public void totalSupplyNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(TOTAL_SUPPLY_NFT);

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with ownerOf function for NFT")
    public void ownerOfEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(OWNER_OF);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with tokenURI function for NFT")
    public void tokenURIEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(TOKEN_URI);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getFungibleTokenInfo function")
    public void getFungibleTokenInfoEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                GET_FUNGIBLE_TOKEN_INFO.getSelector(),
                parameters,
                senderAccountId,
                GET_FUNGIBLE_TOKEN_INFO.getActualGas());
    }

    @Then("I call estimateGas with getNonFungibleTokenInfo function")
    public void getNonFungibleTokenInfoEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addInt64(1L);

        validateGasEstimation(
                precompileContractId,
                GET_NON_FUNGIBLE_TOKEN_INFO.getSelector(),
                parameters,
                senderAccountId,
                GET_NON_FUNGIBLE_TOKEN_INFO.getActualGas());
    }

    @Then("I call estimateGas with getTokenInfo function for fungible")
    public void getTokenInfoEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                GET_TOKEN_INFO.getSelector(),
                parameters,
                senderAccountId,
                GET_TOKEN_INFO.getActualGas());
    }

    @Then("I call estimateGas with getTokenInfo function for NFT")
    public void getTokenInfoNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                precompileContractId,
                GET_TOKEN_INFO_NFT.getSelector(),
                parameters,
                senderAccountId,
                GET_TOKEN_INFO_NFT.getActualGas());
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for fungible token")
    public void getTokenDefaultFreezeStatusFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_FREEZE_STATUS);

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for NFT")
    public void getTokenDefaultFreezeStatusNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_FREEZE_STATUS);

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for fungible token")
    public void getTokenDefaultKycStatusFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_KYC_STATUS);

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for NFT")
    public void getTokenDefaultKycStatusNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        var methodInterface = getFlaggedValue(GET_TOKEN_DEFAULT_KYC_STATUS);

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with isKyc function for fungible token")
    public void isKycFungibleEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(IS_KYC);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with isKyc function for NFT")
    public void isKycNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(IS_KYC);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with isFrozen function for fungible token")
    public void isFrozenFungibleEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(IS_FROZEN);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with isFrozen function for NFT")
    public void isFrozenNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(IS_FROZEN);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenType function for fungible token")
    public void getTokenTypeFungibleEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(GET_TOKEN_TYPE);

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with getTokenType function for NFT")
    public void getTokenTypeNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(GET_TOKEN_TYPE);

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with redirect balanceOf function")
    public void redirectBalanceOfEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_BALANCE_OF.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_BALANCE_OF.getActualGas());
    }

    @Then("I call estimateGas with redirect name function")
    public void redirectNameEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_NAME.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_NAME.getActualGas());
    }

    @Then("I call estimateGas with redirect symbol function")
    public void redirectSymbolEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_SYMBOL.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_SYMBOL.getActualGas());
    }

    @Then("I call estimateGas with redirect name function for NFT")
    public void redirectNameNonFungibleEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_NAME.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_NAME.getActualGas());
    }

    @Then("I call estimateGas with redirect symbol function for NFT")
    public void redirectSymbolNonFungibleEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters().addAddress(nonFungibleKycUnfrozenAddressString);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_SYMBOL.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_SYMBOL.getActualGas());
    }

    @Then("I call estimateGas with redirect decimals function")
    public void redirectDecimalsEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters().addAddress(fungibleKycUnfrozenTokenIdAddressString);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_DECIMALS.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_DECIMALS.getActualGas());
    }

    @Then("I call estimateGas with redirect allowance function")
    public void redirectAllowanceEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_ALLOWANCE.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_ALLOWANCE.getActualGas());
    }

    @Then("I call estimateGas with redirect getOwnerOf function")
    public void redirectGetOwnerOfEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_GET_OWNER_OF.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_GET_OWNER_OF.getActualGas());
    }

    @Then("I call estimateGas with redirect tokenURI function")
    public void redirectTokenURIEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_TOKEN_URI.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_TOKEN_URI.getActualGas());
    }

    @Then("I call estimateGas with redirect isApprovedForAll function")
    public void redirectIsApprovedForAllEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL.getActualGas());
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
    public void redirectTransferEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("5"));

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_TRANSFER.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_TRANSFER.getActualGas());
    }

    @Then("I call estimateGas with redirect transferFrom function")
    public void redirectTransferFromEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(new BigInteger("5"));

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_TRANSFER_FROM.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_TRANSFER_FROM.getActualGas());
    }

    @Then("I call estimateGas with redirect approve function")
    public void redirectApproveEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.TEN);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_APPROVE.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_APPROVE.getActualGas());
    }

    @And("I approve for all nft for precompile contract")
    public void approveForAllNftForPrecompileContract() {
        var contractId = TestUtil.fromSolidityAddress(precompileTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveNftAllSerials(nonFungibleTokenId, contractId);
    }

    @Then("I call estimateGas with redirect transferFrom NFT function")
    public void redirectTransferFromNonFungibleEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT);
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.TWO);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with redirect setApprovalForAll function")
    public void redirectSetApprovalForAllEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias)
                .addBool(true);

        validateGasEstimation(
                precompileContractId,
                REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL.getSelector(),
                parameters,
                senderAccountId,
                REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL.getActualGas());
    }

    @Then("I call estimateGas with pseudo random seed")
    public void pseudoRandomSeedEstimateGas() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters();

        validateGasEstimation(
                estimatePrecompileContractId,
                PSEUDO_RANDOM_SEED.getSelector(),
                parameters,
                senderAccountId,
                PSEUDO_RANDOM_SEED.getActualGas());
    }

    @Then("I call estimateGas with pseudo random number")
    public void pseudoRandomNumberEstimateGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(PSEUDO_RANDOM_NUMBER);

        var parameters = new ContractFunctionParameters().addUint32(500).addUint32(1000);

        validateGasEstimation(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimateGas with exchange rate tinycents to tinybars")
    public void exchangeRateTinyCentsToTinyBarsEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addUint256(new BigInteger("100"));

        validateGasEstimation(
                estimatePrecompileContractId,
                EXCHANGE_RATE_TINYCENTS_TO_TINYBARS.getSelector(),
                parameters,
                senderAccountId,
                EXCHANGE_RATE_TINYCENTS_TO_TINYBARS.getActualGas());
    }

    @Then("I call estimateGas with exchange rate tinybars to tinycents")
    public void exchangeRateTinyBarsToTinyCentsEstimateGas() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addUint256(new BigInteger("100"));

        validateGasEstimation(
                estimatePrecompileContractId,
                EXCHANGE_RATE_TINYBARS_TO_TINYCENTS.getSelector(),
                parameters,
                senderAccountId,
                EXCHANGE_RATE_TINYBARS_TO_TINYCENTS.getActualGas());
    }

    private void executeContractTransaction(
            DeployedContract deployedContract, long gas, ContractMethodInterface contractMethods, byte[] parameters) {

        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedContract.contractId(), gas, contractMethods.getSelector(), parameters, null);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I call estimateGas with balanceOf function for {token} and verify the estimated gas against HAPI")
    public void executeBalanceOfFunctionWithLimitedGas(TokenNameEnum tokenName)
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(BALANCE_OF);
        var tokenId = tokenClient.getToken(tokenName).tokenId();

        var data = encodeDataToByteArray(ERC, methodInterface, asAddress(tokenId), asAddress(admin));

        var parameters = new ContractFunctionParameters()
                .addAddress(asAddress(tokenId).toString())
                .addAddress(adminAddressString);
        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                ercTestContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas(),
                Optional.empty());
        assertWithinDeviation(methodInterface.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedErcTestContract, estimateGasResult, methodInterface, data);
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
    public void executeTransferForFungibleWithGasLimit() throws ExecutionException, InterruptedException {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_TOKEN,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount),
                5L);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(secondReceiverAddressString)
                .addInt64(5L);

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                TRANSFER_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                TRANSFER_TOKEN.getActualGas(),
                Optional.empty());
        assertWithinDeviation(TRANSFER_TOKEN.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, TRANSFER_TOKEN, data);
    }

    @And("I associate the contract with the receiver account")
    public void associateSecondReceiverWithContract() {
        networkTransactionResponse = tokenClient.associate(secondReceiverAccount, nonFungibleTokenId);
    }

    @Then("I call estimateGas with transferNFT function and verify the estimated gas against HAPI")
    public void executeTransferTokenNonFungibleWithGasLimit() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(TRANSFER_NFT);
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                asAddress(nonFungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount.getAccountId().toEvmAddress()),
                2L);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(asAddress(secondReceiverAccount.getAccountId().toEvmAddress())
                        .toString())
                .addInt64(2L);

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas(),
                Optional.empty());
        assertWithinDeviation(methodInterface.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, methodInterface, data);
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
    public void executeAllowanceFungibleWithLimitedGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(ALLOWANCE);
        final var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));

        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleKycUnfrozenTokenIdAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias);

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas(),
                Optional.empty());
        assertWithinDeviation(methodInterface.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, methodInterface, data);
    }

    @Then("I call estimateGas with allowance function for NFT and verify the estimated gas against HAPI")
    public void executeAllowanceNonFungibleWithLimitedGas() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(ALLOWANCE);
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias);

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas(),
                Optional.empty());
        assertWithinDeviation(methodInterface.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, methodInterface, data);
    }

    @Then("I call estimateGas with approve function and verify the estimated gas against HAPI")
    public void executeApproveWithLimitedGas() throws ExecutionException, InterruptedException {
        final var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                APPROVE,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                BigInteger.TEN);

        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.TEN);

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                APPROVE.getSelector(),
                parameters,
                senderAccountId,
                APPROVE.getActualGas(),
                Optional.empty());
        assertWithinDeviation(APPROVE.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, APPROVE, data);
    }

    @Then("I call estimateGas with approveNFT function and verify the estimated gas against HAPI")
    public void executeApproveNftWithLimitedGas() throws ExecutionException, InterruptedException {
        final var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                APPROVE_NFT,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias),
                BigInteger.ONE);

        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.ONE);

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                APPROVE_NFT.getSelector(),
                parameters,
                senderAccountId,
                APPROVE_NFT.getActualGas(),
                Optional.empty());
        assertWithinDeviation(APPROVE_NFT.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, APPROVE_NFT, data);
    }

    @Then("I call estimateGas with transferFrom function with fungible and verify the estimated gas against HAPI")
    public void executeTransferFromWithGasLimit() throws ExecutionException, InterruptedException {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount.getAccountId().toEvmAddress()),
                new BigInteger("5"));

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(asAddress(secondReceiverAccount.getAccountId().toEvmAddress())
                        .toString())
                .addUint256(new BigInteger("5"));

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                TRANSFER_FROM.getSelector(),
                parameters,
                senderAccountId,
                TRANSFER_FROM.getActualGas(),
                Optional.empty());
        assertWithinDeviation(TRANSFER_FROM.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, TRANSFER_FROM, data);
    }

    @Then("I call estimateGas with transferFromNFT function and verify the estimated gas against HAPI")
    public void executeTransferFromNFTWithGasLimit() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(TRANSFER_FROM_NFT);
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                methodInterface,
                asAddress(nonFungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount.getAccountId().toEvmAddress()),
                new BigInteger("3"));

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(asAddress(secondReceiverAccount.getAccountId().toEvmAddress())
                        .toString())
                .addUint256(new BigInteger("3"));

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas(),
                Optional.empty());
        assertWithinDeviation(methodInterface.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, methodInterface, data);
    }

    @Then("I call estimate gas that mints FUNGIBLE token and gets the total supply and balance")
    public void estimateGasMintFungibleTokenGetTotalSupplyAndBalanceOfTreasury()
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE);

        final var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addInt64(1L)
                .addBytesArray(new byte[][] {})
                .addAddress(adminAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimate gas that mints NFT token and gets the total supply and balance")
    public void estimateGasMintNftTokenGetTotalSupplyAndBalanceOfTreasury()
            throws ExecutionException, InterruptedException {

        final var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addInt64(0L)
                .addBytesArray(asByteArray(List.of("0x02")))
                .addAddress(adminAddressString);

        validateGasEstimation(
                precompileContractId,
                MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE.getSelector(),
                parameters,
                senderAccountId,
                MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE.getActualGas());
    }

    @Then("I call estimate gas that burns FUNGIBLE token and gets the total supply and balance")
    public void estimateGasBurnFungibleTokenGetTotalSupplyAndBalanceOfTreasury()
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addInt64(1L)
                .addInt64Array(asLongArray(List.of()))
                .addAddress(adminAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimate gas that burns NFT token and returns the total supply and balance of treasury")
    public void estimateGasBurnNftTokenGetTotalSupplyAndBalanceOfTreasury()
            throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addInt64(0L)
                .addInt64Array(asLongArray(List.of(1L)))
                .addAddress(adminAddressString);

        validateGasEstimation(
                precompileContractId,
                BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE.getSelector(),
                parameters,
                senderAccountId,
                BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE.getActualGas());
    }

    @Then("I call estimate gas that wipes FUNGIBLE token and gets the total supply and balance")
    public void estimateGasWipeFungibleTokenGetTotalSupplyAndBalanceOfTreasury()
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addInt64(1L)
                .addInt64Array(asLongArray(List.of()))
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimate gas that wipes NFT token and gets the total supply and balance")
    public void estimateGasWipeNftTokenGetTotalSupplyAndBalanceOfTreasury()
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addInt64(0L)
                .addInt64Array(asLongArray(List.of(1L)))
                .addAddress(receiverAccountAlias);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimate gas that pauses FUNGIBLE token, unpauses and gets the token status")
    public void estimateGasPauseFungibleTokenGetStatusUnpauseGetStatus()
            throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(fungibleTokenAddressString);

        validateGasEstimation(
                precompileContractId,
                PAUSE_UNPAUSE_GET_STATUS.getSelector(),
                parameters,
                senderAccountId,
                PAUSE_UNPAUSE_GET_STATUS.getActualGas());
    }

    @Then("I call estimate gas that pauses NFT token, unpauses and gets the token status")
    public void estimateGasPauseNFTTokenGetStatusUnpauseGetStatus() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters().addAddress(nonFungibleTokenAddressString);

        validateGasEstimation(
                precompileContractId,
                PAUSE_UNPAUSE_GET_STATUS.getSelector(),
                parameters,
                senderAccountId,
                PAUSE_UNPAUSE_GET_STATUS.getActualGas());
    }

    @Then("I call estimate gas that freezes FUNGIBLE token, unfreezes and gets freeze status")
    public void estimateGasFreezeFungibleTokenGetFreezeStatusUnfreezeGetFreezeStatus()
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(FREEZE_UNFREEZE_GET_STATUS);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(adminAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimate gas that freezes NFT token, unfreezes and gets freeze status")
    public void estimateGasFreezeNftTokenGetFreezeStatusUnfreezeGetFreezeStatus()
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(FREEZE_UNFREEZE_GET_STATUS);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(adminAddressString);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimate gas that approves FUNGIBLE token and gets allowance")
    public void estimateGasApproveFungibleTokenGetAllowance() throws ExecutionException, InterruptedException {

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.ONE)
                .addUint256(BigInteger.ZERO);

        validateGasEstimation(
                precompileContractId,
                APPROVE_FUNGIBLE_GET_ALLOWANCE.getSelector(),
                parameters,
                senderAccountId,
                APPROVE_FUNGIBLE_GET_ALLOWANCE.getActualGas());
    }

    @Then("I call estimate gas that approves NFT token and gets allowance")
    public void estimateGasApproveNFTTokenGetAllowance() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleKycUnfrozenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.ZERO)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                precompileContractId,
                APPROVE_NFT_GET_ALLOWANCE.getSelector(),
                parameters,
                senderAccountId,
                APPROVE_NFT_GET_ALLOWANCE.getActualGas());
    }

    @Then("I call estimate gas that associates FUNGIBLE token dissociates and fails token transfer")
    public void estimateGasAssociateFungibleTokenDissociateFailTransfer()
            throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.ONE)
                .addUint256(BigInteger.ZERO);

        validateGasEstimation(
                precompileContractId,
                DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER.getSelector(),
                parameters,
                senderAccountId,
                DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER.getActualGas());
    }

    @Then("I call estimate gas that associates NFT token dissociates and fails token transfer")
    public void estimateGasAssociateNftTokenDissociateFailTransfer() throws ExecutionException, InterruptedException {
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(adminAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.ZERO)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                precompileContractId,
                DISSOCIATE_NFT_AND_TRANSFER.getSelector(),
                parameters,
                senderAccountId,
                DISSOCIATE_NFT_AND_TRANSFER.getActualGas());
    }

    @Then("I call estimate gas that approves a FUNGIBLE token and transfers it")
    public void estimateGasApproveFungibleTokenTransferFromGetAllowanceGetBalance()
            throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER);

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addAddress(receiverAccountAlias)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
    }

    @Then("I call estimate gas that approves a NFT token and transfers it")
    public void approveNftTokenTransferFromGetAllowanceGetBalance() throws ExecutionException, InterruptedException {
        final var methodInterface = getFlaggedValue(APPROVE_NFT_TOKEN_AND_TRANSFER_FROM);

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addAddress(secondReceiverAddressString)
                .addUint256(BigInteger.ONE);

        validateGasEstimation(
                precompileContractId,
                methodInterface.getSelector(),
                parameters,
                senderAccountId,
                methodInterface.getActualGas());
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
    public void executeMintFungibleTokenWithLimitedGas() throws ExecutionException, InterruptedException {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, MINT_TOKEN, asAddress(fungibleTokenId), 1L, asByteArray(new ArrayList<>()));

        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenAddressString)
                .addInt64(1L)
                .addBytesArray(asByteArray(new ArrayList<>()));

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                MINT_TOKEN.getSelector(),
                parameters,
                senderAccountId,
                MINT_TOKEN.getActualGas(),
                Optional.empty());
        assertWithinDeviation(MINT_TOKEN.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, MINT_TOKEN, data);
    }

    @Then("I call estimateGas with mintToken function for NFT and verify the estimated gas against HAPI")
    public void executeMintNonFungibleWithLimitedGas() throws ExecutionException, InterruptedException {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, MINT_NFT, asAddress(nonFungibleTokenId), 0L, asByteArray(List.of("0x02")));

        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenAddressString)
                .addInt64(0L)
                .addBytesArray(asByteArray(List.of("0x02")));

        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(
                estimatePrecompileContractId,
                MINT_NFT.getSelector(),
                parameters,
                senderAccountId,
                MINT_NFT.getActualGas(),
                Optional.empty());
        assertWithinDeviation(MINT_NFT.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasResult, MINT_NFT, data);
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
        ALLOWANCE("allowanceExternal", 25399),
        ALLOWANCE_ERC("allowance", 27481),
        APPROVE("approveExternal", 729571),
        APPROVE_NFT("approveNFTExternal", 729569),
        APPROVE_ERC("approve", 731632),
        ASSOCIATE_TOKEN("associateTokenExternal", 729374),
        ASSOCIATE_TOKENS("associateTokensExternal", 730641),
        BALANCE_OF("balanceOf", 27270),
        BALANCE_OF_NFT("balanceOfIERC721", 27228),
        BURN_TOKEN("burnTokenExternal", 40247),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenPublic", 192752),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES("createFungibleTokenWithCustomFeesPublic", 176628),
        CREATE_NFT("createNonFungibleTokenPublic", 192472),
        CREATE_NFT_WITH_CUSTOM_FEES("createNonFungibleTokenWithCustomFeesPublic", 195579),
        CRYPTO_TRANSFER("cryptoTransferExternal", 47206),
        CRYPTO_TRANSFER_HBARS("cryptoTransferExternal", 31819),
        CRYPTO_TRANSFER_NFT("cryptoTransferExternal", 60258),
        DECIMALS("decimals", 27143),
        DELETE_TOKEN("deleteTokenExternal", 39095),
        DISSOCIATE_AND_ASSOCIATE("dissociateAndAssociateTokenExternal", 1434814),
        DISSOCIATE_TOKEN("dissociateTokenExternal", 729428),
        DISSOCIATE_TOKENS("dissociateTokensExternal", 730641),
        EXCHANGE_RATE_TINYCENTS_TO_TINYBARS("tinycentsToTinybars", 24833),
        EXCHANGE_RATE_TINYBARS_TO_TINYCENTS("tinybarsToTinycents", 24811),
        FREEZE_TOKEN("freezeTokenExternal", 39339),
        GET_APPROVED("getApprovedExternal", 25192),
        GET_APPROVED_ERC("getApproved", 27393),
        GET_FUNGIBLE_TOKEN_INFO("getInformationForFungibleToken", 56456),
        GET_NON_FUNGIBLE_TOKEN_INFO("getInformationForNonFungibleToken", 59159),
        GET_TOKEN_DEFAULT_FREEZE_STATUS("getTokenDefaultFreeze", 25191),
        GET_TOKEN_DEFAULT_KYC_STATUS("getTokenDefaultKyc", 25267),
        GET_TOKEN_EXPIRY_INFO("getTokenExpiryInfoExternal", 25617),
        GET_TOKEN_INFO("getInformationForToken", 55815),
        GET_TOKEN_INFO_NFT("getInformationForToken", 56523),
        GET_TOKEN_KEY("getTokenKeyExternal", 27024),
        GET_TOKEN_TYPE("getType", 25223),
        GRANT_KYC("grantTokenKycExternal", 39311),
        IS_APPROVED_FOR_ALL("isApprovedForAllExternal", 25483),
        IS_APPROVED_FOR_ALL_ERC("isApprovedForAll", 27520),
        IS_TOKEN("isTokenExternal", 25100),
        IS_FROZEN("isTokenFrozen", 25473),
        IS_KYC("isKycGranted", 25417),
        MINT_TOKEN("mintTokenExternal", 40700),
        MINT_NFT("mintTokenExternal", 309748),
        NAME("name", 27976),
        NAME_NFT("nameIERC721", 27837),
        NESTED_ASSOCIATE("nestedAssociateTokenExternal", 0),
        NESTED_FREEZE_UNFREEZE("nestedFreezeUnfreezeTokenExternal", 54548),
        NESTED_GRANT_REVOKE_KYC("nestedGrantAndRevokeTokenKYCExternal", 54516),
        OWNER_OF("getOwnerOf", 27271),
        PSEUDO_RANDOM_SEED("getPseudorandomSeed", 36270),
        PSEUDO_RANDOM_NUMBER("getPseudorandomNumber", 36729),
        REVOKE_KYC("revokeTokenKycExternal", 39324),
        REDIRECT_FOR_TOKEN_ALLOWANCE("allowanceRedirect", 33182),
        REDIRECT_FOR_TOKEN_APPROVE("approveRedirect", 737257),
        REDIRECT_FOR_TOKEN_BALANCE_OF("balanceOfRedirect", 32806),
        REDIRECT_FOR_TOKEN_DECIMALS("decimalsRedirect", 32411),
        REDIRECT_FOR_TOKEN_GET_APPROVED("getApprovedRedirect", 23422),
        REDIRECT_FOR_TOKEN_GET_OWNER_OF("getOwnerOfRedirect", 32728),
        REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL("isApprovedForAllRedirect", 33204),
        REDIRECT_FOR_TOKEN_NAME("nameNFTRedirect", 33615),
        REDIRECT_FOR_TOKEN_SYMBOL("symbolNFTRedirect", 33681),
        REDIRECT_FOR_TOKEN_NAME_NFT("nameRedirect", 23422),
        REDIRECT_FOR_TOKEN_SYMBOL_NFT("symbolRedirect", 23422),
        REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL("setApprovalForAllRedirect", 737243),
        REDIRECT_FOR_TOKEN_TOTAL_SUPPLY("totalSupplyRedirect", 23422),
        REDIRECT_FOR_TOKEN_TOKEN_URI("tokenURIRedirect", 33997),
        REDIRECT_FOR_TOKEN_TRANSFER("transferRedirect", 47842),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM("transferFromRedirect", 48274),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT("transferFromNFTRedirect", 62336),
        SET_APPROVAL_FOR_ALL("setApprovalForAllExternal", 729608),
        SYMBOL("symbol", 27815),
        SYMBOL_NFT("symbolIERC721", 27814),
        TOTAL_SUPPLY("totalSupply", 27100),
        TOTAL_SUPPLY_NFT("totalSupplyIERC721", 27078),
        TOKEN_URI("tokenURI", 27856),
        TRANSFER_ERC("transfer", 42138),
        TRANSFER_FROM("transferFromExternal", 41307),
        TRANSFER_FROM_ERC("transferFrom", 42475),
        TRANSFER_FROM_NFT("transferFromNFTExternal", 55478),
        TRANSFER_NFT("transferNFTExternal", 54596),
        TRANSFER_NFTS("transferNFTsExternal", 58999),
        TRANSFER_TOKEN("transferTokenExternal", 39666),
        TRANSFER_TOKENS("transferTokensExternal", 48326),
        UNFREEZE_TOKEN("unfreezeTokenExternal", 39323),
        WIPE_TOKEN_ACCOUNT("wipeTokenAccountExternal", 39496),
        WIPE_NFT_ACCOUNT("wipeTokenAccountNFTExternal", 40394),
        PAUSE_TOKEN("pauseTokenExternal", 39112),
        PAUSE_UNPAUSE_NESTED_TOKEN("nestedPauseUnpauseTokenExternal", 54237),
        UNPAUSE_TOKEN("unpauseTokenExternal", 39112),
        UPDATE_TOKEN_EXPIRY("updateTokenExpiryInfoExternal", 39699),
        UPDATE_TOKEN_INFO("updateTokenInfoExternal", 74920),
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", 60427),
        MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 68127),
        MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 335855),
        BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 66908),
        BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 66886),
        WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 88477),
        WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 88970),
        PAUSE_UNPAUSE_GET_STATUS("pauseTokenGetPauseStatusUnpauseGetPauseStatus", 98345),
        FREEZE_UNFREEZE_GET_STATUS("freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus", 57387),
        APPROVE_FUNGIBLE_GET_ALLOWANCE("approveTokenGetAllowance", 733080),
        APPROVE_NFT_GET_ALLOWANCE("approveTokenGetAllowance", 733127),
        DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER("associateTokenDissociateFailTransfer", 1482987),
        DISSOCIATE_NFT_AND_TRANSFER("associateTokenDissociateFailTransfer", 1525177),
        APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER("approveFungibleTokenTransferFromGetAllowanceGetBalance", 840000),
        APPROVE_NFT_TOKEN_AND_TRANSFER_FROM("approveNftAndTransfer", 835000);

        private final String selector;
        private final int actualGas;
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethodsModularizedServices implements ContractMethodInterface {
        TRANSFER_NFT("transferNFTExternal", 41571),
        ASSOCIATE_TOKENS("associateTokensExternal", 1336847),
        CRYPTO_TRANSFER_NFT("cryptoTransferExternal", 47372),
        GET_TOKEN_EXPIRY_INFO("getTokenExpiryInfoExternal", 28607),
        IS_TOKEN("isTokenExternal", 27977),
        GET_TOKEN_KEY("getTokenKeyExternal", 30778),
        ALLOWANCE_ERC("allowance", 30724),
        GET_APPROVED("getApprovedExternal", 28200),
        GET_APPROVED_ERC("getApproved", 30256),
        IS_APPROVED_FOR_ALL("isApprovedForAllExternal", 28860),
        IS_APPROVED_FOR_ALL_ERC("isApprovedForAll", 30763),
        NAME_NFT("nameIERC721", 30764),
        SYMBOL("symbol", 30742),
        SYMBOL_NFT("symbolIERC721", 30741),
        DECIMALS("decimals", 29998),
        TOTAL_SUPPLY("totalSupply", 29955),
        TOTAL_SUPPLY_NFT("totalSupplyIERC721", 29933),
        OWNER_OF("getOwnerOf", 30266),
        TOKEN_URI("tokenURI", 30923),
        GET_TOKEN_DEFAULT_FREEZE_STATUS("getTokenDefaultFreeze", 27980),
        GET_TOKEN_DEFAULT_KYC_STATUS("getTokenDefaultKyc", 28011),
        IS_KYC("isKycGranted", 28542),
        IS_FROZEN("isTokenFrozen", 28575),
        GET_TOKEN_TYPE("getType", 27991),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT("transferFromNFTRedirect", 47116),
        MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 76875),
        BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 76413),
        WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 101170),
        WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 101792),
        FREEZE_UNFREEZE_GET_STATUS("freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus", 65626),
        PSEUDO_RANDOM_NUMBER("getPseudorandomNumber", 40666),
        TRANSFER_FROM_NFT("transferFromNFTExternal", 42745),
        BALANCE_OF("balanceOf", 30277),
        ALLOWANCE("allowanceExternal", 28778),
        APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER("approveFungibleTokenTransferFromGetAllowanceGetBalance", 840000),
        APPROVE_NFT_TOKEN_AND_TRANSFER_FROM("approveNftAndTransfer", 830000);

        private final String selector;
        private final int actualGas;
    }
}
