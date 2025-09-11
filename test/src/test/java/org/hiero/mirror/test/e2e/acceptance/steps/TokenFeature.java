// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static com.hedera.hashgraph.sdk.Status.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.rest.model.TransactionTypes.CRYPTOTRANSFER;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.hiero.mirror.rest.model.AssessedCustomFee;
import org.hiero.mirror.rest.model.CustomFees;
import org.hiero.mirror.rest.model.FixedFee;
import org.hiero.mirror.rest.model.FractionalFee;
import org.hiero.mirror.rest.model.FractionalFeeAmount;
import org.hiero.mirror.rest.model.Nft;
import org.hiero.mirror.rest.model.NftAllowance;
import org.hiero.mirror.rest.model.NftAllowancesResponse;
import org.hiero.mirror.rest.model.NftTransactionHistory;
import org.hiero.mirror.rest.model.NftTransactionTransfer;
import org.hiero.mirror.rest.model.TokenAirdrop;
import org.hiero.mirror.rest.model.TokenAllowance;
import org.hiero.mirror.rest.model.TokenInfo;
import org.hiero.mirror.rest.model.TokenInfo.PauseStatusEnum;
import org.hiero.mirror.rest.model.TokenRelationship;
import org.hiero.mirror.rest.model.TokenRelationship.FreezeStatusEnum;
import org.hiero.mirror.rest.model.TokenRelationship.KycStatusEnum;
import org.hiero.mirror.rest.model.TokenRelationshipResponse;
import org.hiero.mirror.rest.model.TokensResponse;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.hiero.mirror.rest.model.TransactionDetail;
import org.hiero.mirror.rest.model.TransactionNftTransfersInner;
import org.hiero.mirror.rest.model.TransactionTokenTransfersInner;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenResponse;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

@CustomLog
@RequiredArgsConstructor
public class TokenFeature extends AbstractFeature {

    private final AcceptanceTestProperties properties;
    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;

    private final Map<TokenId, List<NftInfo>> tokenNftInfoMap = new HashMap<>();

    private List<CustomFee> customFees = List.of();

    @Getter
    private TokenId tokenId;

    private TokenResponse tokenResponse;

    private TokensResponse tokensWithTreasuryAccount;

    private TokensResponse tokensAssociatedWithPublicKey;

    private TransactionDetail transactionDetail;

    @Given("I ensure token {token} has been created")
    public void createNamedToken(TokenNameEnum tokenName) {
        var tokenAndResponse = tokenClient.getToken(tokenName);
        if (tokenAndResponse.response() != null) {
            this.networkTransactionResponse = tokenAndResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
        var tokenInfo = mirrorClient.getTokenInfo(tokenAndResponse.tokenId().toString());
        log.debug("Get token info for token {}: {}", tokenName, tokenInfo);
    }

    @Given("I ensure NFT {token} has been created")
    public void createNonFungibleToken(TokenClient.TokenNameEnum tokenName) {
        final var result = tokenClient.getToken(tokenName);
        this.networkTransactionResponse = result.response();
        this.tokenId = result.tokenId();
        tokenNftInfoMap.put(tokenId, new ArrayList<>());
    }

    @RetryAsserts
    @Given("I ensure token has the correct properties")
    public void ensureTokenProperties() {
        var tokensResponse = mirrorClient.getTokens(tokenId.toString()).getTokens();
        assertThat(tokensResponse).isNotNull().hasSize(1);
        var token = tokensResponse.getFirst();
        var tokenDecimals = token.getDecimals();
        if (token.getType().equals(TokenType.NON_FUNGIBLE_UNIQUE.toString())) {
            assertThat(tokenDecimals).isZero();
        } else {
            assertThat(tokenDecimals).isEqualTo(10L);
        }
        assertThat(token.getName()).isNotNull();
        log.debug("Get tokens response for token {}: {}", tokenId, tokensResponse);

        var balancesResponse = mirrorClient.getTokenBalances(tokenId.toString()).getBalances();
        assertThat(balancesResponse).isNotNull().hasSize(1);
        var balanceDecimals = balancesResponse.getFirst().getDecimals();
        assertThat(balanceDecimals).isEqualTo(tokenDecimals);
        log.debug("Get token balances for token {}: {}", tokenId, balancesResponse);
    }

    @RetryAsserts
    @Given("I verify the number of nfts for token is expected")
    public void verifyNumOfNFTsForToken() {
        var apiResponse = mirrorClient.getTokenNFTs(tokenId.toString());
        assertThat(apiResponse.getNfts()).isNotNull();

        var nftsInitialSize = apiResponse.getNfts().size();

        tokenClient.mint(tokenId, nextBytes(4));
        assertThat(mirrorClient.getTokenNFTs(tokenId.toString()).getNfts()).hasSize(nftsInitialSize + 1);
    }

    @RetryAsserts
    @Given("I ensure token has the expected metadata and key")
    public void ensureTokenInfoProperties() {
        var tokenInfo = mirrorClient.getTokenInfo(tokenId.toString());
        var tokenInfoMetadataKey = tokenInfo.getMetadataKey() != null
                ? PublicKey.fromString(tokenInfo.getMetadataKey().getKey())
                : null;
        var responseMetadataKey = this.tokenResponse.metadataKey() != null
                ? this.tokenResponse.metadataKey().getPublicKey()
                : null;
        assertThat(tokenInfoMetadataKey).isEqualTo(responseMetadataKey);
        assertThat(tokenInfo.getMetadata()).isEqualTo(this.tokenResponse.metadata());
    }

    @Given("I associate account {account} with token {token}")
    public void associateToken(AccountNameEnum accountName, TokenNameEnum tokenName) {
        var accountId = accountClient.getAccount(accountName);
        var associatedTokenId = tokenClient.getToken(tokenName).tokenId();
        associateWithToken(accountId, associatedTokenId);
    }

    @Given("I approve {account} to transfer up to {long} of token {token}")
    public void setFungibleTokenAllowance(AccountNameEnum accountName, long amount, TokenNameEnum tokenName) {
        var spenderAccountId = accountClient.getAccount(accountName).getAccountId();
        var allowanceTokenId = tokenClient.getToken(tokenName).tokenId();
        networkTransactionResponse = accountClient.approveToken(allowanceTokenId, spenderAccountId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I approve {account} to all serials of the NFT")
    public void setNonFungibleTokenAllowance(AccountClient.AccountNameEnum accountName) {
        var spenderAccountId = accountClient.getAccount(accountName).getAccountId();
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenId, spenderAccountId);
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
    }

    @Then("the mirror node REST API should confirm the approved allowance {long} of {token} for {account}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenAllowanceResponse(
            long approvedAmount, TokenNameEnum tokenName, AccountNameEnum accountName) {
        var allowanceTokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderAccountId = accountClient.getAccount(accountName);
        verifyMirrorAPIApprovedTokenAllowanceResponse(allowanceTokenId, spenderAccountId, approvedAmount, 0);
    }

    @Then(
            "the mirror node REST API should confirm the approved allowance of NFT {token} and {account} when owner is {string}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenAllowanceResponse(
            TokenClient.TokenNameEnum tokenName, AccountClient.AccountNameEnum accountName, String owner) {
        var allowanceTokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderId = accountClient.getAccount(accountName);
        var isOwner = owner.equalsIgnoreCase("true");
        verifyMirrorAPIApprovedNftAllowanceResponse(allowanceTokenId, spenderId, true, isOwner);
    }

    @Then("the mirror node REST API should confirm the approved allowance for {token} and {account} no longer exists")
    @RetryAsserts
    public void verifyTokenAllowanceDelete(TokenNameEnum tokenName, AccountNameEnum accountName) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var spender = accountClient.getAccount(accountName).getAccountId().toString();
        var token = tokenClient.getToken(tokenName).tokenId().toString();

        var mirrorTokenAllowanceResponse = mirrorClient.getAccountTokenAllowanceBySpender(owner, token, spender);
        assertThat(mirrorTokenAllowanceResponse.getAllowances()).isEmpty();
    }

    @Then(
            "the mirror node REST API should confirm the approved allowance for NFT {token} and {account} is no longer available")
    @RetryAsserts
    public void verifyNftAllowanceDelete(
            TokenClient.TokenNameEnum tokenName, AccountClient.AccountNameEnum spenderName) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var spender = accountClient.getAccount(spenderName).getAccountId().toString();
        var token = tokenClient.getToken(tokenName).tokenId().toString();

        var mirrorNftAllowanceResponse = mirrorClient.getAccountNftAllowanceByOwner(owner, token, spender);

        assertThat(mirrorNftAllowanceResponse.getAllowances()).isEmpty();
    }

    @Then("the mirror node REST API should confirm the debit of {long} from {token} allowance of {long} for {account}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedDebitedTokenAllowanceResponse(
            long debitAmount, TokenNameEnum tokenName, long approvedAmount, AccountNameEnum accountName) {
        var allowanceTokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderAccountId = accountClient.getAccount(accountName);
        verifyMirrorAPIApprovedTokenAllowanceResponse(allowanceTokenId, spenderAccountId, approvedAmount, debitAmount);
    }

    @Then("I transfer {long} of token {token} to {account}")
    public void transferTokensToRecipient(long amount, TokenNameEnum tokenName, AccountNameEnum accountName) {
        var transferTokenId = tokenClient.getToken(tokenName).tokenId();
        var recipientAccountId = accountClient.getAccount(accountName).getAccountId();
        var ownerAccountId = tokenClient.getSdkClient().getExpandedOperatorAccountId();

        networkTransactionResponse = tokenClient.transferFungibleToken(
                transferTokenId,
                ownerAccountId.getAccountId(),
                ownerAccountId,
                recipientAccountId,
                amount,
                false,
                null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("{account} transfers {long} of token {token} to {account}")
    public void transferFromAllowance(
            AccountNameEnum spender, long amount, TokenNameEnum token, AccountNameEnum recipient) {
        var transferTokenId = tokenClient.getToken(token).tokenId();
        var spenderAccountId = accountClient.getAccount(spender);
        var recipientAccountId = accountClient.getAccount(recipient).getAccountId();
        var ownerAccountId = accountClient.getClient().getOperatorAccountId();

        networkTransactionResponse = tokenClient.transferFungibleToken(
                transferTokenId, ownerAccountId, spenderAccountId, recipientAccountId, amount, true, null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should confirm the transfer of {long} {token}")
    @RetryAsserts
    public void verifyMirrorAPITokenTransferResponse(long transferAmount, TokenNameEnum tokenName) {
        verifyMirrorAPIApprovedTokenTransferResponse(transferAmount, tokenName, false);
    }

    @Then("the mirror node REST API should confirm the approved transfer of {long} {token}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenTransferResponse(long transferAmount, TokenNameEnum tokenName) {
        verifyMirrorAPIApprovedTokenTransferResponse(transferAmount, tokenName, true);
    }

    private void verifyMirrorAPIApprovedTokenTransferResponse(
            long transferAmount, TokenNameEnum tokenName, boolean isApproval) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        var transferTokenId = tokenClient.getToken(tokenName).tokenId().toString();
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var expectedTokenTransfer = new TransactionTokenTransfersInner()
                .tokenId(transferTokenId)
                .account(owner)
                .amount(-transferAmount)
                .isApproval(isApproval);
        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).hasSize(1).first().satisfies(t -> assertThat(t.getTokenTransfers())
                .contains(expectedTokenTransfer));
    }

    @Given("I delete the allowance on token {token} for {account}")
    public void setFungibleTokenAllowance(TokenNameEnum tokenName, AccountNameEnum spenderAccountName) {
        var allowanceTokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderAccountId = accountClient.getAccount(spenderAccountName);
        networkTransactionResponse = accountClient.approveToken(allowanceTokenId, spenderAccountId.getAccountId(), 0L);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the allowance on NFT {token} for spender {account}")
    public void deleteNonFungibleTokenAllowance(
            TokenClient.TokenNameEnum tokenName, AccountClient.AccountNameEnum spenderAccountName) {
        var allowanceTokenId = tokenClient.getToken(tokenName).tokenId();
        var owner = accountClient.getClient().getOperatorAccountId();
        var spenderAccountId = accountClient.getAccount(spenderAccountName);
        networkTransactionResponse =
                accountClient.deleteNftAllowance(allowanceTokenId, owner, spenderAccountId.getAccountId());
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
    }

    @Given("I successfully create a new token with custom fees schedule")
    public void createNewToken(List<CustomFee> customFees) {
        this.tokenResponse = tokenClient.getToken(TokenNameEnum.FUNGIBLE_WITH_CUSTOM_FEES, customFees);
        this.tokenId = tokenResponse.tokenId();
        this.networkTransactionResponse = tokenResponse.response();
        this.customFees = customFees;
    }

    @Given("I successfully create a new unfrozen and granted kyc token")
    public void createNewToken() {
        this.tokensWithTreasuryAccount = mirrorClient.getTokensWithTreasuryAccount(
                tokenClient.getAccountId().toString());
        this.tokensAssociatedWithPublicKey = mirrorClient.getTokensAssociatedWithPublicKey(
                tokenClient.getAccountId().getPublicKey().toString());

        this.tokenResponse = tokenClient.getToken(TokenNameEnum.FUNGIBLE_KYC_UNFROZEN_2);
        this.tokenId = tokenResponse.tokenId();
        this.networkTransactionResponse = tokenResponse.response();
    }

    // We need this type of token to test automatic association upon claim airdrop
    @Given("I successfully create a new unfrozen token with KYC not applicable")
    public void createNewTokenKycNotApplicable() {
        this.tokenResponse = tokenClient.getToken(TokenNameEnum.FUNGIBLE_AIRDROP);
        this.tokenId = tokenResponse.tokenId();
        this.networkTransactionResponse = tokenResponse.response();
    }

    @When("I verify the number of tokens with given treasury account")
    public void getAllTokensForAccount() {
        TokensResponse tokensByAccountId = mirrorClient.getTokensWithTreasuryAccount(
                tokenClient.getAccountId().toString());
        assertThat(tokensByAccountId.getTokens()).isNotNull();
        assertThat(this.tokensWithTreasuryAccount.getTokens().size() + 1)
                .isEqualTo(tokensByAccountId.getTokens().size());
        assertThat(this.tokenResponse.tokenId().toString())
                .isEqualTo(tokensByAccountId.getTokens().getLast().getTokenId());

        TokensResponse tokensByPublicKey = mirrorClient.getTokensAssociatedWithPublicKey(
                tokenClient.getAccountId().getPublicKey().toString());
        assertThat(tokensByPublicKey.getTokens()).isNotNull();
        assertThat(tokensByPublicKey.getTokens().stream()
                        .filter(t -> t.getTokenId()
                                .equals(this.tokensAssociatedWithPublicKey
                                        .getTokens()
                                        .getLast()
                                        .getTokenId()))
                        .findFirst())
                .isNotNull();
    }

    @When("I verify the number of tokens associated with account")
    public void getAllTokensByPublicKey() {
        TokensResponse tokensAssociatedWithPubKey = mirrorClient.getTokensAssociatedWithPublicKey(
                tokenClient.getAccountId().getPublicKey().toString());
        assertThat(this.tokensAssociatedWithPublicKey.getTokens().size() + 1)
                .isEqualTo(tokensAssociatedWithPubKey.getTokens().size());
    }

    @Given("I successfully create a new nft {token} with infinite supplyType")
    public void createNewNft(TokenNameEnum tokenNameEnum) {
        this.tokenResponse = tokenClient.getToken(tokenNameEnum);
        this.networkTransactionResponse = tokenResponse.response();
        this.tokenId = tokenResponse.tokenId();
        tokenNftInfoMap.put(tokenId, new ArrayList<>());
    }

    @Given("I associate {account} with token")
    public void associateWithToken(AccountNameEnum accountName) {
        var accountId = accountClient.getAccount(accountName);
        associateWithToken(accountId, tokenId);
    }

    @When("I set account freeze status to {int} for {account}")
    public void setFreezeStatus(int freezeStatus, AccountNameEnum accountName) {
        setFreezeStatus(freezeStatus, accountClient.getAccount(accountName));
    }

    @When("I set account kyc status to {int} for {account}")
    public void setKycStatus(int kycStatus, AccountNameEnum accountName) {
        setKycStatus(kycStatus, accountClient.getAccount(accountName));
    }

    @Then("I transfer {int} tokens to {account}")
    public void transferTokensToRecipient(int amount, AccountNameEnum accountName) {
        transferTokens(tokenId, amount, null, accountName);
    }

    @Then("I transfer serial number index {int} to {account}")
    public void transferNftsToRecipient(int serialNumberIndex, AccountNameEnum recipient) {
        var ownerAccountId = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        var serialNumbers =
                List.of(tokenNftInfoMap.get(tokenId).get(serialNumberIndex).serialNumber());
        var recipientAccountId = accountClient.getAccount(recipient).getAccountId();

        transferNfts(this.tokenId, serialNumbers, ownerAccountId, recipientAccountId, false);
    }

    @Then("{account} transfers NFT {token} to {account} with approval={bool}")
    public void transferNftsToRecipient(
            AccountNameEnum spender, TokenNameEnum token, AccountNameEnum recipient, boolean isApproval) {
        var nftTokenId = tokenClient.getToken(token).tokenId();
        var serialNumbers = getSerialsForToken(nftTokenId);
        var spenderAccountId = accountClient.getAccount(spender);
        var recipientAccountId = accountClient.getAccount(recipient).getAccountId();

        transferNfts(nftTokenId, serialNumbers, spenderAccountId, recipientAccountId, isApproval);
    }

    @Then("{account} transfers {int} tokens to {account} with fractional fee {int}")
    public void transferTokensFromSenderToRecipientWithFee(
            AccountNameEnum sender, int amount, AccountNameEnum accountName, int fractionalFee) {
        transferTokens(tokenId, amount, sender, accountName, fractionalFee);
    }

    @Given("I update the token")
    public void updateToken() {
        var operatorId = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.updateToken(tokenId, operatorId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I pause the token")
    public void pauseToken() {
        networkTransactionResponse = tokenClient.pause(tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I unpause the token")
    public void unpauseToken() {
        networkTransactionResponse = tokenClient.unpause(tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I update the token metadata")
    public void updateTokenMetadata() {
        var newMetadata = nextBytes(4);
        networkTransactionResponse = tokenClient.updateTokenMetadata(tokenId, tokenResponse.metadataKey(), newMetadata);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        this.tokenResponse =
                this.tokenResponse.toBuilder().metadata(newMetadata).build();
    }

    @Given("I update the token metadata key")
    public void updateTokenMetadataKey() {
        var newMetadataKey = PrivateKey.generateED25519();
        networkTransactionResponse = tokenClient.updateTokenMetadataKey(tokenId, newMetadataKey);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        this.tokenResponse =
                this.tokenResponse.toBuilder().metadataKey(newMetadataKey).build();
    }

    @Given("I update the treasury of token to operator")
    public void updateTokenTreasuryToOperator() {
        try {
            var accountId = accountClient.getAccount(AccountNameEnum.OPERATOR);
            networkTransactionResponse = tokenClient.updateTokenTreasury(tokenId, accountId);
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(ReceiptStatusException.class);
            ReceiptStatusException actualException = (ReceiptStatusException) exception;
            assertThat(actualException.receipt.status).isEqualTo(CURRENT_TREASURY_STILL_OWNS_NFTS);
        }
    }

    @Given("I update the treasury of token to {account}")
    public void updateTokenTreasury(AccountNameEnum accountNameEnum) {
        try {
            var accountId = accountClient.getAccount(accountNameEnum);
            networkTransactionResponse = tokenClient.updateTokenTreasury(tokenId, accountId);
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(ReceiptStatusException.class);
            ReceiptStatusException actualException = (ReceiptStatusException) exception;
            assertThat(actualException.receipt.status).isEqualTo(CURRENT_TREASURY_STILL_OWNS_NFTS);
        }
    }

    @Given("I burn {int} from the token")
    public void burnToken(int amount) {
        networkTransactionResponse = tokenClient.burnFungible(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("{account} rejects the fungible token")
    public void rejectFungibleToken(AccountNameEnum ownerName) {
        var owner = accountClient.getAccount(ownerName);
        networkTransactionResponse = tokenClient.rejectFungibleToken(List.of(tokenId), owner);
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
    }

    @RetryAsserts
    @Then("the mirror node REST API should return the transaction {account} returns {int} fungible token to {account}")
    public void verifyTokenTransferForRejectedFungibleToken(
            AccountNameEnum senderName, long amount, AccountNameEnum treasuryName) {
        var sender = accountClient.getAccount(senderName).getAccountId();
        var treasury = accountClient.getAccount(treasuryName).getAccountId();

        var transactionDetails = verifyTransactions();
        assertThat(transactionDetails.getTokenTransfers())
                .containsExactlyInAnyOrder(
                        new TransactionTokenTransfersInner()
                                .account(sender.toString())
                                .amount(-amount)
                                .isApproval(false)
                                .tokenId(tokenId.toString()),
                        new TransactionTokenTransfersInner()
                                .account(treasury.toString())
                                .amount(amount)
                                .isApproval(false)
                                .tokenId(tokenId.toString()));
        assertThat(getTokenBalance(sender, tokenId)).isZero();
    }

    @Given("{account} rejects serial number index {int}")
    public void rejectNonFungibleToken(AccountNameEnum ownerName, int index) {
        long serialNumber = tokenNftInfoMap.get(tokenId).get(index).serialNumber();
        var nftId = new NftId(tokenId, serialNumber);
        var owner = accountClient.getAccount(ownerName);

        networkTransactionResponse = tokenClient.rejectNonFungibleToken(List.of(nftId), owner);
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
    }

    @Then("I airdrop serial number index {int} to {account}")
    public void airdropNonFungibleToken(int index, AccountNameEnum accountName) {
        var serialNumber = tokenNftInfoMap.get(tokenId).get(index).serialNumber();
        var receiver = accountClient.getAccount(accountName);
        var sender = accountClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = verify(tokenClient.executeNftAirdrop(tokenId, sender, receiver, serialNumber));
    }

    @RetryAsserts
    @Then(
            "the mirror node REST API should return the transaction {account} returns serial number index {int} to {account}")
    public void verifyTokenTransferForRejectedNft(AccountNameEnum senderName, int index, AccountNameEnum treasuryName) {
        var sender = accountClient.getAccount(senderName).getAccountId();
        var treasury = accountClient.getAccount(treasuryName).getAccountId();
        long serialNumber = tokenNftInfoMap.get(tokenId).get(index).serialNumber();

        var transactionDetails = verifyTransactions();
        assertThat(transactionDetails.getNftTransfers())
                .containsExactly(new TransactionNftTransfersInner()
                        .isApproval(false)
                        .receiverAccountId(treasury.toString())
                        .senderAccountId(sender.toString())
                        .serialNumber(serialNumber)
                        .tokenId(tokenId.toString()));
        assertThat(getTokenBalance(sender, tokenId)).isZero();
    }

    @Given("I burn serial number index {int} from token")
    public void burnNft(int serialNumberIndex) {
        networkTransactionResponse = tokenClient.burnNonFungible(
                tokenId, tokenNftInfoMap.get(tokenId).get(serialNumberIndex).serialNumber());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    public void mintToken(int amount) {
        networkTransactionResponse = tokenClient.mint(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint a serial number from the token")
    public void mintNftToken() {
        var metadata = nextBytes(4);
        networkTransactionResponse = tokenClient.mint(tokenId, metadata);
        assertNotNull(networkTransactionResponse.getTransactionId());
        TransactionReceipt receipt = networkTransactionResponse.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();
        long serialNumber = receipt.serials.getFirst();
        assertThat(serialNumber).isPositive();
        tokenNftInfoMap.get(tokenId).add(new NftInfo(serialNumber, metadata));
    }

    @Given("I update the metadata for serial number indices {int} and {int}")
    public void updateNftMetadata(int serialNumberIndex1, int serialNumberIndex2) {
        updateNftMetadataForSerials(serialNumberIndex1, serialNumberIndex2);
    }

    @Given("I update the metadata for serial number index {int}")
    public void updateNftMetadata(int serialNumberIndex) {
        updateNftMetadataForSerials(serialNumberIndex);
    }

    @Given("I wipe {int} from the token for {account}")
    public void wipeToken(int amount, AccountNameEnum accountName) {
        networkTransactionResponse = tokenClient.wipeFungible(tokenId, amount, accountClient.getAccount(accountName));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe serial number index {int} from token for {account}")
    public void wipeNft(int serialNumberIndex, AccountNameEnum accountName) {
        networkTransactionResponse = tokenClient.wipeNonFungible(
                tokenId,
                tokenNftInfoMap.get(tokenId).get(serialNumberIndex).serialNumber(),
                accountClient.getAccount(accountName));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I dissociate {account} from the token")
    public void dissociateNewAccountFromToken(AccountNameEnum accountName) {
        networkTransactionResponse = tokenClient.dissociate(accountClient.getAccount(accountName), tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the token")
    public void deleteToken() {
        var operatorId = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.delete(operatorId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I update token with new custom fees schedule")
    public void updateTokenFeeSchedule(List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.updateTokenFeeSchedule(tokenId, admin, customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        this.customFees = customFees;
    }

    @Then("the mirror node Token Info REST API should return pause status {pauseStatus}")
    @RetryAsserts
    public void verifyTokenPauseStatus(PauseStatusEnum pauseStatus) {
        verifyTokenPauseStatus(tokenId, pauseStatus);
    }

    @Then("the mirror node REST API should return the transaction")
    @RetryAsserts
    public void verifyMirrorAPIResponses() {
        verifyTransactions();
    }

    @Then("the mirror node REST API should return the transaction and get transaction detail")
    @RetryAsserts
    public void verifyMirrorAPIResponsesAndGetTransactionDetail() {
        transactionDetail = verifyTransactions();
    }

    @Then("the mirror node REST API should return the transaction for token serial number index {int} transaction flow")
    @RetryAsserts
    public void verifyMirrorNftTransactionsAPIResponses(Integer serialNumberIndex) {
        var tokenNftInfo = tokenNftInfoMap.get(tokenId).get(getIndexOrDefault(serialNumberIndex));
        var serialNumber = tokenNftInfo.serialNumber();
        verifyTransactions();
        verifyNft(tokenId, serialNumber, tokenNftInfo.metadata());
        verifyNftTransactions(tokenId, serialNumber);
    }

    @Then("the mirror node REST API should return the transaction for token fund flow")
    @RetryAsserts
    public void verifyMirrorTokenFundFlow() {
        verifyMirrorTokenFundFlow(tokenId, Collections.emptyList());
    }

    @Then("the mirror node REST API should return the transaction for token fund flow with assessed custom fees")
    @RetryAsserts
    public void verifyMirrorTokenFundFlow(List<AssessedCustomFee> assessedCustomFees) {
        verifyMirrorTokenFundFlow(tokenId, assessedCustomFees);
    }

    private void verifyMirrorTokenFundFlow(TokenId tokenId, List<AssessedCustomFee> assessedCustomFees) {
        verifyTransactions(assessedCustomFees);
        verifyToken(tokenId);
        verifyTokenTransfers(tokenId);
    }

    @Then("the mirror node REST API should return the transaction for token serial number index {int} full flow")
    @RetryAsserts
    public void verifyMirrorNftFundFlow(Integer serialNumberIndex) {
        var tokenNftInfo = tokenNftInfoMap.get(tokenId).get(getIndexOrDefault(serialNumberIndex));
        var serialNumber = tokenNftInfo.serialNumber();
        verifyTransactions();
        verifyToken(tokenId);
        verifyNft(tokenId, serialNumber, tokenNftInfo.metadata());
        verifyNftTransfers(tokenId, serialNumber);
        verifyNftTransactions(tokenId, serialNumber);
    }

    @Then("the mirror node REST API should confirm token update")
    @RetryAsserts
    public void verifyMirrorTokenUpdateFlow() {
        verifyTokenUpdate(tokenId);
    }

    @Then("the mirror node REST API should return the transaction for transaction {string}")
    @RetryAsserts
    public void verifyMirrorRestTransactionIsPresent(int status, String transactionIdString) {
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionIdString);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.getFirst();
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionIdString);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }
    }

    @Then("the mirror node REST API should confirm token with custom fees schedule")
    @RetryAsserts
    public void verifyMirrorTokenWithCustomFeesSchedule() {
        var transaction = verifyTransactions();
        verifyTokenWithCustomFeesSchedule(tokenId, transaction.getConsensusTimestamp());
    }

    @Then("the mirror node REST API should return the token relationship for token for {account}")
    @RetryAsserts
    public void verifyMirrorTokenRelationshipTokenAPIResponses(AccountNameEnum accountName) {
        TokenRelationshipResponse mirrorTokenRelationship = callTokenRelationship(tokenId, accountName);
        // Asserting values
        assertTokenRelationship(mirrorTokenRelationship);
        TokenRelationship token = mirrorTokenRelationship.getTokens().getFirst();
        assertThat(token.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(token.getFreezeStatus()).isEqualTo(FreezeStatusEnum.UNFROZEN);
        assertThat(token.getKycStatus()).isEqualTo(KycStatusEnum.REVOKED);
        assertThat(token.getDecimals()).isNotNull();
    }

    @Then("the mirror node REST API should return the token relationship for nft for {account}")
    @RetryAsserts
    public void verifyMirrorTokenRelationshipNftAPIResponses(AccountNameEnum accountName) {
        TokenRelationshipResponse mirrorTokenRelationship = callTokenRelationship(tokenId, accountName);
        // Asserting values
        assertTokenRelationship(mirrorTokenRelationship);
        TokenRelationship token = mirrorTokenRelationship.getTokens().getFirst();
        assertThat(token.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(token.getFreezeStatus()).isEqualTo(FreezeStatusEnum.NOT_APPLICABLE);
        assertThat(token.getKycStatus()).isEqualTo(KycStatusEnum.NOT_APPLICABLE);
    }

    @Then("the mirror node REST API should confirm the approved transfer of {long} tokens")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenTransferResponse(long transferAmount) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var expectedTokenTransfer = new TransactionTokenTransfersInner()
                .tokenId(tokenId.toString())
                .account(owner)
                .amount(-transferAmount)
                .isApproval(true);
        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).hasSize(1).first().satisfies(t -> assertThat(t.getTokenTransfers())
                .contains(expectedTokenTransfer));
    }

    @Then("the mirror node REST API should confirm the NFT transfer and confirm the new owner is {account}")
    @RetryAsserts
    public void verifyMirrorNftTransfer(AccountClient.AccountNameEnum accountName) {
        Long serialNumber = tokenNftInfoMap.get(tokenId).getFirst().serialNumber();
        var recipientId = accountClient.getAccount(accountName);
        verifyTransactions();
        verifyNftTransfers(tokenId, serialNumber, recipientId);
        verifyNftTransactions(tokenId, serialNumber);
    }

    @Then("I airdrop {int} tokens to {account}")
    public void airdropTokens(int amount, AccountNameEnum accountName) {
        var receiver = accountClient.getAccount(accountName);
        var sender = accountClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse =
                verify(tokenClient.executeFungibleTokenAirdrop(tokenId, sender, receiver.getAccountId(), amount));
    }

    @RetryAsserts
    @Then("I verify {string} airdrop of {int} tokens to {account}")
    public void verifyFungibleTokenAirdrop(String status, int amount, AccountNameEnum receiverName) {
        var receiver = accountClient.getAccount(receiverName);
        var sender = accountClient.getAccount(AccountNameEnum.OPERATOR);

        switch (status) {
            case "successful" -> verifySuccessfulAirdrop(tokenId, sender, receiver, amount);
            case "pending" -> verifyPendingAirdrop(tokenId, sender, receiver, amount);
            case "cancelled" -> verifyCancelledAirdrop(tokenId, sender, receiver);
            default -> throw new IllegalArgumentException("Invalid airdrop status");
        }
    }

    @RetryAsserts
    @Then("I verify {string} airdrop of serial number index {int} to {account}")
    public void verifyNftTokenAirdrop(String status, int index, AccountNameEnum receiverName) {
        var serialNumber = tokenNftInfoMap.get(tokenId).get(index).serialNumber();
        var receiver = accountClient.getAccount(receiverName);
        var sender = accountClient.getAccount(AccountNameEnum.OPERATOR);

        switch (status) {
            case "successful" -> verifySuccessfulNFTAirdrop(tokenId, sender, receiver, serialNumber);
            case "pending" -> verifyPendingNftAirdrop(tokenId, sender, receiver, serialNumber);
            case "cancelled" -> verifyCancelledNftAirdrop(tokenId, sender, receiver, serialNumber);
            default -> throw new IllegalArgumentException("Invalid airdrop status");
        }
    }

    @Then("I cancel the airdrop to {account}")
    public void cancelPendingFungibleTokenAirdrop(AccountNameEnum accountName) {
        var receiver = accountClient.getAccount(accountName);
        var sender = accountClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse =
                verify(tokenClient.executeCancelTokenAirdrop(sender, receiver.getAccountId(), tokenId));
    }

    @Then("I cancel the NFT with serial number index {int} airdrop to {account}")
    public void cancelNftPendingAirdrop(int index, AccountNameEnum accountName) {
        var serialNumber = tokenNftInfoMap.get(tokenId).get(index).serialNumber();
        var receiver = accountClient.getAccount(accountName);
        var sender = accountClient.getSdkClient().getExpandedOperatorAccountId();
        var nftId = new NftId(tokenId, serialNumber);
        networkTransactionResponse =
                verify(tokenClient.executeCancelNftAirdrop(sender, receiver.getAccountId(), nftId));
    }

    @Then("{account} claims the airdrop")
    public void claimPendingFungibleAirdrop(AccountNameEnum accountName) {
        var receiver = accountClient.getAccount(accountName);
        var sender = accountClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = verify(tokenClient.executeClaimTokenAirdrop(sender, receiver, tokenId));
    }

    @Then("{account} claims airdrop for NFT with serial number index {int}")
    public void claimPendingNftAirdrop(AccountNameEnum accountName, int index) {
        var serialNumber = tokenNftInfoMap.get(tokenId).get(index).serialNumber();
        var receiver = accountClient.getAccount(accountName);
        var sender = accountClient.getSdkClient().getExpandedOperatorAccountId();
        var nftId = new NftId(tokenId, serialNumber);
        networkTransactionResponse = verify(tokenClient.executeClaimNftAirdrop(sender, receiver, nftId));
    }

    private void associateWithToken(ExpandedAccountId accountId, TokenId tokenId) {
        networkTransactionResponse = tokenClient.associate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setFreezeStatus(int freezeStatus, ExpandedAccountId accountId) {
        if (freezeStatus == TokenFreezeStatus.Frozen_VALUE) {
            networkTransactionResponse = tokenClient.freeze(tokenId, accountId.getAccountId());
        } else if (freezeStatus == TokenFreezeStatus.Unfrozen_VALUE) {
            networkTransactionResponse = tokenClient.unfreeze(tokenId, accountId.getAccountId());
        } else {
            log.warn("Freeze Status must be set to 1 (Frozen) or 2 (Unfrozen)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setKycStatus(int kycStatus, ExpandedAccountId accountId) {
        if (kycStatus == TokenKycStatus.Granted_VALUE) {
            networkTransactionResponse = tokenClient.grantKyc(tokenId, accountId.getAccountId());
        } else if (kycStatus == TokenKycStatus.Revoked_VALUE) {
            networkTransactionResponse = tokenClient.revokeKyc(tokenId, accountId.getAccountId());
        } else {
            log.warn("Kyc Status must be set to 1 (Granted) or 2 (Revoked)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void transferTokens(TokenId tokenId, int amount, AccountNameEnum sender, AccountNameEnum receiver) {
        transferTokens(tokenId, amount, sender, receiver, 0);
    }

    private void transferTokens(
            TokenId tokenId, int amount, AccountNameEnum senderName, AccountNameEnum recieverName, int fractionalFee) {
        var sender = senderName != null
                ? accountClient.getAccount(senderName)
                : tokenClient.getSdkClient().getExpandedOperatorAccountId();
        var receiver = accountClient.getAccount(recieverName).getAccountId();
        long startingBalance = getTokenBalance(receiver, tokenId);
        long expectedBalance = startingBalance + amount - fractionalFee;

        networkTransactionResponse = tokenClient.transferFungibleToken(tokenId, sender, receiver, null, amount);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertThat(getTokenBalance(receiver, tokenId)).isEqualTo(expectedBalance);
    }

    private long getTokenBalance(AccountId accountId, TokenId tokenId) {
        verifyTransactions(null);
        var tokenRelationships =
                mirrorClient.getTokenRelationships(accountId, tokenId).getTokens();
        assertThat(tokenRelationships).isNotNull().hasSize(1);
        return tokenRelationships.getFirst().getBalance();
    }

    private void transferNfts(
            TokenId tokenId,
            List<Long> serialNumbers,
            ExpandedAccountId senderAccountId,
            AccountId recipientAccountId,
            boolean isApproval) {
        var ownerAccountId = accountClient.getClient().getOperatorAccountId();
        long startingBalance = getTokenBalance(recipientAccountId, tokenId);

        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                tokenId,
                senderAccountId,
                recipientAccountId,
                serialNumbers,
                senderAccountId.getPrivateKey(),
                ownerAccountId,
                isApproval);

        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
        assertThat(getTokenBalance(recipientAccountId, tokenId)).isEqualTo(startingBalance + serialNumbers.size());
    }

    private TransactionDetail verifyTransactions() {
        return verifyTransactions(Collections.emptyList());
    }

    private TransactionDetail verifyTransactions(List<AssessedCustomFee> assessedCustomFees) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.getFirst();
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");

        if (!CollectionUtils.isEmpty(assessedCustomFees)) {
            assertThat(mirrorTransaction.getAssessedCustomFees())
                    .containsExactlyInAnyOrderElementsOf(assessedCustomFees);
        }

        return mirrorTransaction;
    }

    private NftTransactionTransfer verifyNftTransactions(TokenId tokenId, Long serialNumber) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        NftTransactionHistory mirrorTransactionsResponse = mirrorClient.getNftTransactions(tokenId, serialNumber);

        List<NftTransactionTransfer> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        NftTransactionTransfer mirrorTransaction = transactions.getFirst();
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }

    private TokenInfo verifyToken(TokenId tokenId) {
        TokenInfo mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());

        return mirrorToken;
    }

    private Nft verifyNft(TokenId tokenId, Long serialNumber, byte[] metadata) {
        Nft mirrorNft = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);

        assertNotNull(mirrorNft);
        assertThat(mirrorNft.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(mirrorNft.getSerialNumber()).isEqualTo(serialNumber);
        assertThat(mirrorNft.getMetadata()).isEqualTo(metadata);
        return mirrorNft;
    }

    private void verifyTokenTransfers(TokenId tokenId) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.getFirst();
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getName()).isEqualTo(CRYPTOTRANSFER);

        boolean tokenIdFound = false;

        String tokenIdString = tokenId.toString();
        for (TransactionTokenTransfersInner tokenTransfer : mirrorTransaction.getTokenTransfers()) {
            if (tokenTransfer.getTokenId().equalsIgnoreCase(tokenIdString)) {
                tokenIdFound = true;
                break;
            }
        }

        assertTrue(tokenIdFound);
    }

    private void verifyNftTransfers(TokenId tokenId, Long serialNumber) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.getFirst();
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());

        assertThat(mirrorTransaction.getNftTransfers())
                .filteredOn(transfer -> tokenId.toString().equals(transfer.getTokenId()))
                .map(TransactionNftTransfersInner::getSerialNumber)
                .containsExactly(serialNumber);
    }

    private void verifyNftTransfers(TokenId tokenId, Long serialNumber, ExpandedAccountId accountId) {
        verifyNftTransfers(tokenId, serialNumber);

        var nftInfo = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);
        assertThat(nftInfo.getAccountId()).isEqualTo(accountId.toString());
    }

    private void verifyTokenUpdate(TokenId tokenId) {
        TokenInfo mirrorToken = verifyToken(tokenId);

        assertThat(mirrorToken.getCreatedTimestamp()).isNotEqualTo(mirrorToken.getModifiedTimestamp());
    }

    private void verifyTokenPauseStatus(TokenId tokenId, PauseStatusEnum pauseStatus) {
        TokenInfo mirrorToken = verifyToken(tokenId);

        assertThat(mirrorToken.getPauseStatus()).isEqualTo(pauseStatus);
    }

    private void verifyTokenWithCustomFeesSchedule(TokenId tokenId, String createdTimestamp) {
        TokenInfo response = verifyToken(tokenId);

        CustomFees expected = new CustomFees()
                .createdTimestamp(createdTimestamp)
                .fixedFees(new ArrayList<>())
                .fractionalFees(new ArrayList<>());

        for (CustomFee customFee : customFees) {
            if (customFee instanceof CustomFixedFee sdkFixedFee) {
                FixedFee fixedFee = new FixedFee();

                fixedFee.allCollectorsAreExempt(false);
                fixedFee.amount(sdkFixedFee.getAmount());
                fixedFee.collectorAccountId(
                        sdkFixedFee.getFeeCollectorAccountId().toString());

                if (sdkFixedFee.getDenominatingTokenId() != null) {
                    fixedFee.denominatingTokenId(
                            sdkFixedFee.getDenominatingTokenId().toString());
                }

                expected.getFixedFees().add(fixedFee);
            } else {
                CustomFractionalFee sdkFractionalFee = (CustomFractionalFee) customFee;
                FractionalFee fractionalFee = new FractionalFee();

                FractionalFeeAmount fraction = new FractionalFeeAmount();
                fraction.numerator(sdkFractionalFee.getNumerator());
                fraction.denominator(sdkFractionalFee.getDenominator());
                fractionalFee.allCollectorsAreExempt(false);
                fractionalFee.amount(fraction);

                fractionalFee.collectorAccountId(
                        sdkFractionalFee.getFeeCollectorAccountId().toString());
                fractionalFee.denominatingTokenId(tokenId.toString());

                if (sdkFractionalFee.getMax() != 0) {
                    fractionalFee.maximum(sdkFractionalFee.getMax());
                }

                fractionalFee.minimum(sdkFractionalFee.getMin());

                expected.getFractionalFees().add(fractionalFee);
            }
        }

        CustomFees actual = response.getCustomFees();

        assertThat(actual)
                .usingRecursiveComparison(RecursiveComparisonConfiguration.builder()
                        .withIgnoreCollectionOrder(true)
                        .build())
                .isEqualTo(expected);
    }

    private void verifyMirrorAPIApprovedTokenAllowanceResponse(
            TokenId tokenId, ExpandedAccountId spenderAccountId, long approvedAmount, long transferAmount) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var token = tokenId.toString();
        var spender = spenderAccountId.getAccountId().toString();
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var mirrorTokenAllowanceResponse = mirrorClient.getAccountTokenAllowanceBySpender(owner, token, spender);
        var remainingAmount = approvedAmount - transferAmount;

        // verify valid set of allowance
        assertThat(mirrorTokenAllowanceResponse.getAllowances())
                .isNotEmpty()
                .first()
                .isNotNull()
                .returns(remainingAmount, TokenAllowance::getAmount)
                .returns(approvedAmount, TokenAllowance::getAmountGranted)
                .returns(owner, TokenAllowance::getOwner)
                .returns(spender, TokenAllowance::getSpender)
                .returns(token, TokenAllowance::getTokenId)
                .extracting(TokenAllowance::getTimestamp)
                .isNotNull()
                .satisfies(t -> assertThat(t.getFrom()).isNotBlank())
                .satisfies(t -> assertThat(t.getTo()).isBlank());
    }

    private void verifyMirrorAPIApprovedNftAllowanceResponse(
            TokenId tokenId, ExpandedAccountId spenderId, boolean approvedForAll, boolean isOwner) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        NftAllowancesResponse mirrorNftAllowanceResponse;

        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var spender = spenderId.getAccountId().toString();
        var token = tokenId.toString();

        if (isOwner) {
            mirrorNftAllowanceResponse = mirrorClient.getAccountNftAllowanceByOwner(owner, token, spender);
        } else {
            mirrorNftAllowanceResponse = mirrorClient.getAccountNftAllowanceBySpender(spender, token, owner);
        }

        // verify valid set of allowance
        assertThat(mirrorNftAllowanceResponse.getAllowances())
                .isNotEmpty()
                .first()
                .isNotNull()
                .returns(approvedForAll, NftAllowance::getApprovedForAll)
                .returns(owner, NftAllowance::getOwner)
                .returns(spender, NftAllowance::getSpender)
                .returns(token, NftAllowance::getTokenId)
                .extracting(NftAllowance::getTimestamp)
                .isNotNull()
                .satisfies(t -> assertThat(t.getFrom()).isNotBlank())
                .satisfies(t -> assertThat(t.getTo()).isBlank());
    }

    private int getIndexOrDefault(Integer index) {
        return index != null ? index : 0;
    }

    private TokenRelationshipResponse callTokenRelationship(TokenId tokenId, AccountNameEnum accountName) {
        var accountId = accountClient.getAccount(accountName);
        return mirrorClient.getTokenRelationships(accountId.getAccountId(), tokenId);
    }

    private void assertTokenRelationship(TokenRelationshipResponse mirrorTokenRelationship) {
        assertNotNull(mirrorTokenRelationship);
        assertNotNull(mirrorTokenRelationship.getTokens());
        assertNotNull(mirrorTokenRelationship.getLinks());
        assertNotEquals(0, mirrorTokenRelationship.getTokens().size());
        assertThat(mirrorTokenRelationship.getLinks().getNext()).isNull();
    }

    private void updateNftMetadataForSerials(int... serialNumberIndices) {
        var newMetadata = nextBytes(4);
        var nftInfoForToken = tokenNftInfoMap.get(tokenId);
        var serialNumbers = Arrays.stream(serialNumberIndices)
                .mapToObj(nftInfoForToken::get)
                .map(NftInfo::serialNumber)
                .toList();
        networkTransactionResponse =
                tokenClient.updateNftMetadata(tokenId, serialNumbers, tokenResponse.metadataKey(), newMetadata);

        assertNotNull(networkTransactionResponse.getTransactionId());
        TransactionReceipt receipt = networkTransactionResponse.getReceipt();
        assertNotNull(receipt);

        Arrays.stream(serialNumberIndices)
                .forEach(idx -> nftInfoForToken.set(
                        idx,
                        nftInfoForToken.get(idx).toBuilder()
                                .metadata(newMetadata)
                                .build()));
    }

    private List<Long> getSerialsForToken(TokenId tokenId) {
        var nftInfoForToken = tokenNftInfoMap.get(tokenId);
        return nftInfoForToken == null
                ? emptyList()
                : nftInfoForToken.stream().map(NftInfo::serialNumber).toList();
    }

    private NetworkTransactionResponse verify(NetworkTransactionResponse response) {
        assertThat(response.getTransactionId()).isNotNull();
        assertThat(response.getReceipt()).isNotNull();
        networkTransactionResponse = response;
        return response;
    }

    private TokenAirdrop getPendingAirdrop(TokenId tokenId, AccountId sender, AccountId receiver) {
        return mirrorClient.getPendingAirdrops(receiver).getAirdrops().stream()
                .filter(tokenAirdrop -> tokenAirdrop.getTokenId().equals(tokenId.toString())
                        && tokenAirdrop.getSenderId().equals(sender.toString())
                        && tokenAirdrop.getReceiverId().equals(receiver.toString()))
                .findFirst()
                .orElse(null);
    }

    private TokenAirdrop getOutstandingAirdrop(TokenId tokenId, AccountId sender, AccountId receiver) {
        return mirrorClient.getOutstandingAirdrops(sender).getAirdrops().stream()
                .filter(tokenAirdrop -> tokenAirdrop.getTokenId().equals(tokenId.toString())
                        && tokenAirdrop.getSenderId().equals(sender.toString())
                        && tokenAirdrop.getReceiverId().equals(receiver.toString()))
                .findFirst()
                .orElse(null);
    }

    private void verifyTokenAirdrop(TokenAirdrop tokenAirdrop, AccountId sender, AccountId receiver, TokenId tokenId) {
        assertThat(tokenAirdrop)
                .isNotNull()
                .returns(receiver.toString(), TokenAirdrop::getReceiverId)
                .returns(sender.toString(), TokenAirdrop::getSenderId)
                .returns(tokenId.toString(), TokenAirdrop::getTokenId);
    }

    private void verifySuccessfulAirdrop(
            TokenId tokenId, ExpandedAccountId sender, ExpandedAccountId receiver, long amount) {
        assertThat(getPendingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        assertThat(getOutstandingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        // Call the REST api to get the token relationship
        var tokenRelationshipReceiver = mirrorClient.getTokenRelationships(receiver.getAccountId(), tokenId);
        assertThat(tokenRelationshipReceiver.getTokens())
                .hasSize(1)
                .first()
                .returns(tokenId.toString(), TokenRelationship::getTokenId)
                .returns(transactionDetail.getConsensusTimestamp(), TokenRelationship::getCreatedTimestamp)
                .returns(amount, TokenRelationship::getBalance);
        assertThat(getTokenBalance(receiver.getAccountId(), tokenId)).isEqualTo(amount);
    }

    private void verifySuccessfulNFTAirdrop(
            TokenId tokenId, ExpandedAccountId sender, ExpandedAccountId receiver, long serialNumber) {
        assertThat(getPendingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        assertThat(getOutstandingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        // Call the REST api to get the token relationship
        var tokenRelationshipReceiver = mirrorClient.getTokenRelationships(receiver.getAccountId(), tokenId);
        assertThat(tokenRelationshipReceiver.getTokens())
                .hasSize(1)
                .first()
                .returns(tokenId.toString(), TokenRelationship::getTokenId)
                .returns(transactionDetail.getConsensusTimestamp(), TokenRelationship::getCreatedTimestamp)
                .returns(1L, TokenRelationship::getBalance);
        var nftInfo = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);
        assertThat(nftInfo.getAccountId()).isEqualTo(receiver.toString());
        assertThat(getNftAccountRelationship(receiver, tokenId, serialNumber)).isNotNull();
        assertThat(getNftAccountRelationship(sender, tokenId, serialNumber)).isNull();
    }

    private void verifyPendingNftAirdrop(
            TokenId tokenId, ExpandedAccountId sender, ExpandedAccountId receiver, long serialNumber) {
        // Call the REST API to get the pending airdrops for the receiver
        var pendingAirdropForToken = getPendingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId());
        verifyTokenAirdrop(pendingAirdropForToken, sender.getAccountId(), receiver.getAccountId(), tokenId);
        assertThat(pendingAirdropForToken.getSerialNumber()).isEqualTo(serialNumber);
        // Call the REST API to get the outstanding airdrops for the sender
        var outstandingAirdropForToken = getOutstandingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId());
        verifyTokenAirdrop(outstandingAirdropForToken, sender.getAccountId(), receiver.getAccountId(), tokenId);
        assertThat(outstandingAirdropForToken.getSerialNumber()).isEqualTo(serialNumber);
        // Call the REST api to get the nft info
        var nftInfo = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);
        assertThat(nftInfo.getAccountId()).isEqualTo(sender.toString());
        assertThat(getNftAccountRelationship(sender, tokenId, serialNumber)).isNotNull();
        // Call the REST api to get the token relationship
        var tokenRelationshipSender = mirrorClient.getTokenRelationships(sender.getAccountId(), tokenId);
        var tokenRelationshipReceiver = mirrorClient.getTokenRelationships(receiver.getAccountId(), tokenId);
        assertThat(tokenRelationshipSender.getTokens())
                .hasSize(1)
                .first()
                .returns(tokenId.toString(), TokenRelationship::getTokenId);
        assertThat(tokenRelationshipReceiver.getTokens()).isEmpty();
    }

    private void verifyCancelledNftAirdrop(
            TokenId tokenId, ExpandedAccountId sender, ExpandedAccountId receiver, long serialNumber) {
        assertThat(getPendingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        assertThat(getOutstandingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        // Call the REST api to get the token relationship
        var tokenRelationshipReceiver = mirrorClient.getTokenRelationships(receiver.getAccountId(), tokenId);
        assertThat(tokenRelationshipReceiver.getTokens()).isEmpty();
        var nftInfo = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);
        assertThat(nftInfo.getAccountId()).isEqualTo(sender.toString());
        assertThat(getNftAccountRelationship(sender, tokenId, serialNumber)).isNotNull();
        assertThat(getNftAccountRelationship(receiver, tokenId, serialNumber)).isNull();
    }

    private Nft getNftAccountRelationship(ExpandedAccountId owner, TokenId tokenId, long serialNumber) {
        return mirrorClient.getAccountsNftInfo(owner.getAccountId()).getNfts().stream()
                .filter(nft -> nft.getTokenId().equals(tokenId.toString())
                        && nft.getSerialNumber().equals(serialNumber))
                .findFirst()
                .orElse(null);
    }

    private void verifyPendingAirdrop(
            TokenId tokenId, ExpandedAccountId sender, ExpandedAccountId receiver, long amount) {
        // Call the REST API to get the pending airdrops for the receiver
        var pendingAirdropForToken = getPendingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId());
        verifyTokenAirdrop(pendingAirdropForToken, sender.getAccountId(), receiver.getAccountId(), tokenId);
        assertThat(pendingAirdropForToken.getAmount()).isEqualTo(amount);
        // Call the REST API to get the outstanding airdrops for the sender
        var outstandingAirdropForToken = getOutstandingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId());
        verifyTokenAirdrop(outstandingAirdropForToken, sender.getAccountId(), receiver.getAccountId(), tokenId);
        assertThat(outstandingAirdropForToken.getAmount()).isEqualTo(amount);
        // Call the REST api to get the token relationship
        var tokenRelationshipSender = mirrorClient.getTokenRelationships(sender.getAccountId(), tokenId);
        var tokenRelationshipReceiver = mirrorClient.getTokenRelationships(receiver.getAccountId(), tokenId);
        assertThat(tokenRelationshipSender.getTokens())
                .hasSize(1)
                .first()
                .returns(tokenId.toString(), TokenRelationship::getTokenId);
        assertThat(tokenRelationshipReceiver.getTokens()).isEmpty();
    }

    private void verifyCancelledAirdrop(TokenId tokenId, ExpandedAccountId sender, ExpandedAccountId receiver) {
        assertThat(getPendingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        assertThat(getOutstandingAirdrop(tokenId, sender.getAccountId(), receiver.getAccountId()))
                .isNull();
        // Call the REST api to get the token relationship
        var tokenRelationshipReceiver = mirrorClient.getTokenRelationships(receiver.getAccountId(), tokenId);
        assertThat(tokenRelationshipReceiver.getTokens()).isEmpty();
    }

    @Builder(toBuilder = true)
    private record NftInfo(Long serialNumber, byte[] metadata) {}
}
