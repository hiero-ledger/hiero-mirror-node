// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PendingAirdropId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenAirdropTransaction;
import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.TokenCancelAirdropTransaction;
import com.hedera.hashgraph.sdk.TokenClaimAirdropTransaction;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenDeleteTransaction;
import com.hedera.hashgraph.sdk.TokenDissociateTransaction;
import com.hedera.hashgraph.sdk.TokenFeeScheduleUpdateTransaction;
import com.hedera.hashgraph.sdk.TokenFreezeTransaction;
import com.hedera.hashgraph.sdk.TokenGrantKycTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenPauseTransaction;
import com.hedera.hashgraph.sdk.TokenRejectTransaction;
import com.hedera.hashgraph.sdk.TokenRevokeKycTransaction;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TokenUnfreezeTransaction;
import com.hedera.hashgraph.sdk.TokenUnpauseTransaction;
import com.hedera.hashgraph.sdk.TokenUpdateNftsTransaction;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.hashgraph.sdk.TokenWipeTransaction;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.retry.support.RetryTemplate;

@Named
public class TokenClient extends AbstractNetworkClient {

    private final Collection<TokenId> tokenIds = new CopyOnWriteArrayList<>();

    private final Map<TokenNameEnum, TokenResponse> tokenMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TokenAccount, NetworkTransactionResponse> associations = new ConcurrentHashMap<>();
    private final PrivateKey initialMetadataKey = PrivateKey.generateECDSA();
    private final byte[] initialMetadata = nextBytes(4);

    public TokenClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    @Override
    public void clean() {
        if (Boolean.parseBoolean(System.getenv("HIERO_MIRROR_TEST_ACCEPTANCE_SKIP_CLEANUP_ENTITIES"))) {
            // In CI we don't want to cleanup as the entities are needed in the k6 test in the next step.
            log.warn("Acceptance tests running in CI -> skip cleanup.");
            for (var tokenName : tokenMap.keySet()) {
                log.info("Skipping cleanup of token [" + tokenName.getSymbol() + "] at address "
                        + tokenMap.get(tokenName).tokenId().toEvmAddress());
            }
            return;
        }

        var admin = sdkClient.getExpandedOperatorAccountId();
        log.info("Deleting {} tokens and dissociating {} token relationships", tokenIds.size(), associations.size());
        deleteAll(tokenIds, tokenId -> delete(admin, tokenId));
        deleteAll(associations.keySet(), association -> dissociate(association.accountId, association.tokenId));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    public TokenResponse getToken(TokenNameEnum tokenNameEnum) {
        return getToken(tokenNameEnum, List.of());
    }

    public ExpandedAccountId getAccountId() {
        return sdkClient.getExpandedOperatorAccountId();
    }

    public TokenResponse getToken(TokenNameEnum tokenNameEnum, List<CustomFee> customFees) {
        // Create token only if it does not currently exist
        var tokenId = tokenMap.computeIfAbsent(tokenNameEnum, x -> {
            var operator = sdkClient.getExpandedOperatorAccountId();
            try {
                var networkTransactionResponse = createToken(tokenNameEnum, operator, customFees);
                var metadata = tokenNameEnum.createWithMetadata ? initialMetadata : EMPTY_BYTE_ARRAY;
                var metadataKey = tokenNameEnum.createWithMetadata ? initialMetadataKey : null;
                return new TokenResponse(
                        networkTransactionResponse.getReceipt().tokenId,
                        networkTransactionResponse,
                        metadataKey,
                        metadata);
            } catch (Exception e) {
                log.warn("Issue creating additional token: {}, operator: {}, ex:", tokenNameEnum, operator, e);
                return null;
            }
        });

        if (tokenId == null) {
            throw new NetworkException("Null tokenId retrieved from receipt");
        }

        log.debug("Retrieved token: {} with ID: {}", tokenNameEnum, tokenId);
        return tokenMap.get(tokenNameEnum);
    }

    private NetworkTransactionResponse createToken(
            TokenNameEnum tokenNameEnum, ExpandedAccountId expandedAccountId, List<CustomFee> customFees) {
        return createToken(
                tokenNameEnum,
                expandedAccountId,
                expandedAccountId,
                1_000_000,
                TokenSupplyType.INFINITE,
                1,
                tokenNameEnum.tokenType,
                customFees);
    }

    public NetworkTransactionResponse createToken(
            TokenNameEnum tokenNameEnum,
            ExpandedAccountId expandedAccountId,
            ExpandedAccountId treasuryAccount,
            long initialSupply,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
            TokenType tokenType,
            List<CustomFee> customFees) {

        if (tokenType == TokenType.FUNGIBLE_COMMON) {
            return createFungibleToken(
                    tokenNameEnum,
                    expandedAccountId,
                    treasuryAccount,
                    initialSupply,
                    tokenSupplyType,
                    maxSupply,
                    customFees);
        } else {
            return createNonFungibleToken(
                    tokenNameEnum, expandedAccountId, treasuryAccount, tokenSupplyType, maxSupply, customFees);
        }
    }

    private TokenCreateTransaction getTokenCreateTransaction(
            TokenNameEnum tokenNameEnum,
            ExpandedAccountId expandedAccountId,
            ExpandedAccountId treasuryAccount,
            TokenType tokenType,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
            List<CustomFee> customFees) {
        String memo = getMemo("Create token");
        PublicKey adminKey = expandedAccountId.getPublicKey();
        TokenCreateTransaction transaction = new TokenCreateTransaction()
                .setAutoRenewAccountId(expandedAccountId.getAccountId())
                .setAutoRenewPeriod(Duration.ofSeconds(6_999_999L))
                .setTokenMemo(memo)
                .setTokenName(tokenNameEnum.symbol + "_name")
                .setSupplyType(tokenSupplyType)
                .setTokenSymbol(tokenNameEnum.symbol)
                .setTokenType(tokenType)
                .setTreasuryAccountId(treasuryAccount.getAccountId())
                .setTransactionMemo(memo);

        if (tokenSupplyType == TokenSupplyType.FINITE) {
            transaction.setMaxSupply(maxSupply);
        }

        if (adminKey != null) {
            transaction
                    .setAdminKey(adminKey)
                    .setPauseKey(adminKey)
                    .setSupplyKey(adminKey)
                    .setWipeKey(adminKey);
        }

        var freezeStatus = tokenNameEnum.tokenFreezeStatus.getNumber();
        if (freezeStatus > 0 && adminKey != null) {
            transaction
                    .setFreezeDefault(freezeStatus == TokenFreezeStatus.Frozen_VALUE)
                    .setFreezeKey(adminKey);
        }

        var kycStatus = tokenNameEnum.tokenKycStatus.getNumber();
        if (kycStatus > 0 && adminKey != null) {
            transaction.setKycKey(adminKey);
        }

        if (customFees != null && adminKey != null) {
            transaction.setCustomFees(customFees).setFeeScheduleKey(adminKey);
        }

        if (tokenNameEnum.createWithMetadata) {
            transaction.setMetadataKey(initialMetadataKey.getPublicKey());
            transaction.setTokenMetadata(initialMetadata);
        }

        return transaction;
    }

    public NetworkTransactionResponse createFungibleToken(
            TokenNameEnum tokenNameEnum,
            ExpandedAccountId expandedAccountId,
            ExpandedAccountId treasuryAccount,
            long initialSupply,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
            List<CustomFee> customFees) {
        TokenCreateTransaction tokenCreateTransaction = getTokenCreateTransaction(
                        tokenNameEnum,
                        expandedAccountId,
                        treasuryAccount,
                        TokenType.FUNGIBLE_COMMON,
                        tokenSupplyType,
                        maxSupply,
                        customFees)
                .setDecimals(10)
                .setInitialSupply(initialSupply);

        var keyList = KeyList.of(treasuryAccount.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenCreateTransaction, keyList);
        var tokenId = response.getReceipt().tokenId;
        log.info(
                "Created new fungible token {} with symbol {} via {}",
                tokenId,
                tokenNameEnum.symbol,
                response.getTransactionId());
        tokenIds.add(tokenId);
        associations.put(new TokenAccount(tokenId, treasuryAccount), response);
        return response;
    }

    public NetworkTransactionResponse createNonFungibleToken(
            TokenNameEnum tokenNameEnum,
            ExpandedAccountId expandedAccountId,
            ExpandedAccountId treasuryAccount,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
            List<CustomFee> customFees) {
        log.debug("Create new non-fungible token with symbol {}", tokenNameEnum.symbol);
        TokenCreateTransaction tokenCreateTransaction = getTokenCreateTransaction(
                tokenNameEnum,
                expandedAccountId,
                treasuryAccount,
                TokenType.NON_FUNGIBLE_UNIQUE,
                tokenSupplyType,
                maxSupply,
                customFees);

        var keyList = KeyList.of(treasuryAccount.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenCreateTransaction, keyList);
        var tokenId = response.getReceipt().tokenId;
        log.info(
                "Created new NFT {} with symbol {} via {}", tokenId, tokenNameEnum.symbol, response.getTransactionId());
        tokenIds.add(tokenId);
        associations.put(new TokenAccount(tokenId, treasuryAccount), response);
        return response;
    }

    public NetworkTransactionResponse associate(ContractId contractId, TokenId token) {
        return associate(
                new ExpandedAccountId(
                        AccountId.fromString(contractId.toString()),
                        sdkClient.getExpandedOperatorAccountId().getPrivateKey()),
                token);
    }

    public NetworkTransactionResponse associate(ExpandedAccountId accountId, TokenId token) {
        var key = new TokenAccount(token, accountId);

        return associations.computeIfAbsent(key, k -> {
            TokenAssociateTransaction tokenAssociateTransaction = new TokenAssociateTransaction()
                    .setAccountId(accountId.getAccountId())
                    .setTokenIds((List.of(token)))
                    .setTransactionMemo(getMemo("Associate w token"));

            KeyList keyList = (accountId.getPrivateKey() != null) ? KeyList.of(accountId.getPrivateKey()) : null;
            NetworkTransactionResponse response =
                    executeTransactionAndRetrieveReceipt(tokenAssociateTransaction, keyList);

            log.info("Associated account {} with token {} via {}", accountId, token, response.getTransactionId());

            return response;
        });
    }

    public NetworkTransactionResponse mint(TokenId tokenId, long amount) {
        return mint(tokenId, amount, null);
    }

    public NetworkTransactionResponse mint(TokenId tokenId, byte[] metadata) {
        return mint(tokenId, 1, metadata);
    }

    private NetworkTransactionResponse mint(TokenId tokenId, long amount, byte[] metadata) {
        TokenMintTransaction tokenMintTransaction =
                new TokenMintTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Mint token"));

        if (metadata != null) {
            tokenMintTransaction.addMetadata(metadata);
        } else {
            tokenMintTransaction.setAmount(amount);
        }

        var response = executeTransactionAndRetrieveReceipt(tokenMintTransaction);
        log.info("Minted {} tokens to {} via {}", amount, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse updateNftMetadata(
            TokenId tokenId, List<Long> serialNumbers, PrivateKey metadataKey, byte[] metadata) {

        var tokenUpdateNftsTransaction = new TokenUpdateNftsTransaction()
                .setTokenId(tokenId)
                .setSerials(serialNumbers)
                .setMetadata(metadata);

        var response = executeTransactionAndRetrieveReceipt(tokenUpdateNftsTransaction, KeyList.of(metadataKey));
        log.info(
                "Updated NFT metadata on NFT serial numbers {} for token {} via {}",
                serialNumbers,
                tokenId,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse freeze(TokenId tokenId, AccountId accountId) {
        TokenFreezeTransaction tokenFreezeAccountTransaction = new TokenFreezeTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Freeze token account"));

        var response = executeTransactionAndRetrieveReceipt(tokenFreezeAccountTransaction);
        log.info("Froze account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse unfreeze(TokenId tokenId, AccountId accountId) {
        TokenUnfreezeTransaction tokenUnfreezeTransaction = new TokenUnfreezeTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Unfreeze token account"));

        var response = executeTransactionAndRetrieveReceipt(tokenUnfreezeTransaction);
        log.info("Unfroze account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse grantKyc(TokenId tokenId, AccountId accountId) {
        TokenGrantKycTransaction tokenGrantKycTransaction = new TokenGrantKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Grant kyc for token"));

        var response = executeTransactionAndRetrieveReceipt(tokenGrantKycTransaction);
        log.info("Granted KYC for account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse revokeKyc(TokenId tokenId, AccountId accountId) {
        TokenRevokeKycTransaction tokenRevokeKycTransaction = new TokenRevokeKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Revoke kyc for token"));

        var response = executeTransactionAndRetrieveReceipt(tokenRevokeKycTransaction);
        log.info("Revoked KYC for account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse pause(TokenId tokenId) {
        TokenPauseTransaction tokenPauseTransaction =
                new TokenPauseTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Pause token"));

        var response = executeTransactionAndRetrieveReceipt(tokenPauseTransaction);
        log.info("Paused token {} via {}", tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse unpause(TokenId tokenId) {
        TokenUnpauseTransaction tokenUnpauseTransaction =
                new TokenUnpauseTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Unpause token"));

        var response = executeTransactionAndRetrieveReceipt(tokenUnpauseTransaction);
        log.info("Un-paused token {} via {}", tokenId, response.getTransactionId());
        return response;
    }

    private TransferTransaction getFungibleTokenTransferTransaction(
            TokenId tokenId, AccountId owner, AccountId sender, AccountId recipient, long amount, boolean isApproval) {
        var transferTransaction = getTransferTransaction();

        if (isApproval) {
            transferTransaction.addApprovedTokenTransfer(tokenId, owner, Math.negateExact(amount));
        } else {
            transferTransaction.addTokenTransfer(tokenId, sender, Math.negateExact(amount));
        }
        transferTransaction.addTokenTransfer(tokenId, recipient, amount);
        return transferTransaction;
    }

    public TransferTransaction getNonFungibleTokenTransferTransaction(
            TokenId tokenId,
            AccountId sender,
            AccountId recipient,
            List<Long> serialNumbers,
            boolean isApproval,
            AccountId owner) {
        TransferTransaction transaction = getTransferTransaction();
        for (Long serialNumber : serialNumbers) {
            var nftId = new NftId(tokenId, serialNumber);
            if (isApproval) {
                transaction.addApprovedNftTransfer(nftId, owner, recipient);
            } else {
                transaction.addNftTransfer(nftId, sender, recipient);
            }
        }
        return transaction;
    }

    private TransferTransaction getTransferTransaction() {
        return new TransferTransaction().setTransactionMemo(getMemo("Transfer token"));
    }

    public NetworkTransactionResponse transferFungibleToken(
            TokenId tokenId, ExpandedAccountId sender, AccountId recipient, PrivateKey privateKey, long amount) {
        return transferFungibleToken(tokenId, sender.getAccountId(), sender, recipient, amount, false, privateKey);
    }

    public NetworkTransactionResponse transferFungibleToken(
            TokenId tokenId,
            AccountId owner,
            ExpandedAccountId sender,
            AccountId recipient,
            long amount,
            boolean isApproval,
            PrivateKey privateKey) {
        var transaction = getFungibleTokenTransferTransaction(
                tokenId, owner, sender.getAccountId(), recipient, amount, isApproval);
        var response = executeTransactionAndRetrieveReceipt(
                transaction, privateKey == null ? null : KeyList.of(privateKey), sender);
        log.info(
                "Transferred {} tokens of {} from {} to {} via {}, isApproval {}",
                amount,
                tokenId,
                sender,
                recipient,
                response.getTransactionId(),
                isApproval);
        return response;
    }

    public NetworkTransactionResponse transferNonFungibleToken(
            TokenId tokenId,
            ExpandedAccountId sender,
            AccountId recipient,
            List<Long> serialNumbers,
            PrivateKey privateKey,
            AccountId owner,
            boolean isApproval) {
        var transaction = getNonFungibleTokenTransferTransaction(
                tokenId, sender.getAccountId(), recipient, serialNumbers, isApproval, owner);
        var response = executeTransactionAndRetrieveReceipt(
                transaction, privateKey == null ? null : KeyList.of(privateKey), sender);
        log.info(
                "Transferred serial numbers {} of token {} from {} to {} via {}, isApproval {}",
                serialNumbers,
                tokenId,
                sender,
                recipient,
                response.getTransactionId(),
                isApproval);
        return response;
    }

    public NetworkTransactionResponse updateToken(TokenId tokenId, ExpandedAccountId expandedAccountId) {
        PublicKey publicKey = expandedAccountId.getPublicKey();
        String newSymbol = RandomStringUtils.secure().nextAlphabetic(4).toUpperCase();
        String memo = getMemo("Update token");
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setAdminKey(publicKey)
                .setAutoRenewAccountId(expandedAccountId.getAccountId())
                .setAutoRenewPeriod(Duration.ofSeconds(8_000_001L))
                .setTokenName(newSymbol + "_name")
                .setSupplyKey(publicKey)
                .setTokenMemo(memo)
                .setTokenSymbol(newSymbol)
                .setTokenId(tokenId)
                .setTransactionMemo(memo)
                .setTreasuryAccountId(client.getOperatorAccountId())
                .setWipeKey(publicKey);

        var response = executeTransactionAndRetrieveReceipt(tokenUpdateTransaction);
        log.info("Updated token {} with new symbol '{}' via {}", tokenId, newSymbol, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse updateTokenMetadataKey(TokenId tokenId, PrivateKey newMetadataKey) {
        var metadataPublicKey = newMetadataKey.getPublicKey();

        TokenUpdateTransaction tokenUpdateTransaction =
                new TokenUpdateTransaction().setMetadataKey(metadataPublicKey).setTokenId(tokenId);

        var response = executeTransactionAndRetrieveReceipt(tokenUpdateTransaction);
        log.info(
                "Updated token {} with new metadata key {} [{}] via {}",
                tokenId,
                metadataPublicKey,
                Hex.encodeHexString(metadataPublicKey.toBytes()),
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse updateTokenMetadata(TokenId tokenId, PrivateKey metadataKey, byte[] newMetadata) {

        TokenUpdateTransaction tokenUpdateTransaction =
                new TokenUpdateTransaction().setTokenMetadata(newMetadata).setTokenId(tokenId);

        var response = executeTransactionAndRetrieveReceipt(tokenUpdateTransaction, KeyList.of(metadataKey));
        log.info(
                "Updated token {} with new metadata [{}] via {}",
                tokenId,
                Hex.encodeHexString(newMetadata),
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse updateTokenTreasury(TokenId tokenId, ExpandedAccountId newTreasuryId) {
        AccountId treasuryAccountId = newTreasuryId.getAccountId();
        String memo = getMemo("Update token");
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setTokenId(tokenId)
                .setTokenMemo(memo)
                .setTreasuryAccountId(treasuryAccountId)
                .setTransactionMemo(memo);

        KeyList keyList = KeyList.of(newTreasuryId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenUpdateTransaction, keyList);
        log.info(
                "Updated token {} treasury account to {} via {}",
                tokenId,
                treasuryAccountId,
                response.getTransactionId());
        return response;
    }

    private TokenBurnTransaction getTokenBurnTransaction(TokenId tokenId) {
        return new TokenBurnTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Burn token"));
    }

    public NetworkTransactionResponse burnFungible(TokenId tokenId, long amount) {
        TokenBurnTransaction tokenBurnTransaction =
                getTokenBurnTransaction(tokenId).setAmount(amount).setTransactionMemo(getMemo("Token burn"));

        var response = executeTransactionAndRetrieveReceipt(tokenBurnTransaction);
        log.info("Burned amount {} from token {} via {}", amount, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse burnNonFungible(TokenId tokenId, long serialNumber) {
        TokenBurnTransaction tokenBurnTransaction =
                getTokenBurnTransaction(tokenId).addSerial(serialNumber).setTransactionMemo(getMemo("Token burn"));

        var response = executeTransactionAndRetrieveReceipt(tokenBurnTransaction);
        log.info("Burned serial number {} from token {} via {}", serialNumber, tokenId, response.getTransactionId());
        return response;
    }

    private TokenWipeTransaction getTokenWipeTransaction(TokenId tokenId, ExpandedAccountId expandedAccountId) {
        return new TokenWipeTransaction()
                .setAccountId(expandedAccountId.getAccountId())
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Wipe token"));
    }

    public NetworkTransactionResponse wipeFungible(TokenId tokenId, long amount, ExpandedAccountId expandedAccountId) {
        TokenWipeTransaction transaction =
                getTokenWipeTransaction(tokenId, expandedAccountId).setAmount(amount);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info("Wiped {} tokens from account {} via {}", amount, expandedAccountId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse wipeNonFungible(
            TokenId tokenId, long serialNumber, ExpandedAccountId expandedAccountId) {
        TokenWipeTransaction transaction =
                getTokenWipeTransaction(tokenId, expandedAccountId).addSerial(serialNumber);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Wiped serial {} from token {} account {} via {}",
                serialNumber,
                tokenId,
                expandedAccountId,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse dissociate(ExpandedAccountId accountId, TokenId token) {
        TokenDissociateTransaction tokenDissociateTransaction = new TokenDissociateTransaction()
                .setAccountId(accountId.getAccountId())
                .setTokenIds(List.of(token))
                .setTransactionMemo(getMemo("Dissociate token"));

        var keyList = KeyList.of(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenDissociateTransaction, keyList);
        log.info("Dissociated account {} from token {} via {}", accountId, token, response.getTransactionId());
        associations.remove(new TokenAccount(token, accountId));
        return response;
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId, TokenId token) {
        TokenDeleteTransaction tokenDissociateTransaction =
                new TokenDeleteTransaction().setTokenId(token).setTransactionMemo(getMemo("Delete token"));

        var keyList = KeyList.of(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenDissociateTransaction, keyList);
        log.info("Deleted token {} via {}", token, response.getTransactionId());
        tokenIds.remove(token);
        tokenMap.values().removeIf(tokenResponse -> token.equals(tokenResponse.tokenId));
        return response;
    }

    public NetworkTransactionResponse updateTokenFeeSchedule(
            TokenId tokenId, ExpandedAccountId expandedAccountId, List<CustomFee> customFees) {
        TokenFeeScheduleUpdateTransaction transaction = new TokenFeeScheduleUpdateTransaction()
                .setCustomFees(customFees)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Update token fee schedule"));
        var response = executeTransactionAndRetrieveReceipt(transaction, KeyList.of(expandedAccountId.getPrivateKey()));
        log.info("Updated custom fees schedule for token {} via {}", tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse rejectFungibleToken(List<TokenId> tokenIds, ExpandedAccountId owner) {
        var tokenRejectTransaction = new TokenRejectTransaction()
                .setOwnerId(owner.getAccountId())
                .setTokenIds(tokenIds)
                .setTransactionMemo(getMemo("Reject Fungible token"));
        var response = executeTransactionAndRetrieveReceipt(tokenRejectTransaction, KeyList.of(owner.getPrivateKey()));
        log.info("Owner {} rejected fungible tokens {} via {}", owner, tokenIds, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse rejectNonFungibleToken(List<NftId> nftIds, ExpandedAccountId owner) {
        var tokenRejectTransaction = new TokenRejectTransaction()
                .setNftIds(nftIds)
                .setOwnerId(owner.getAccountId())
                .setTransactionMemo(getMemo("Reject NFT"));
        var response = executeTransactionAndRetrieveReceipt(tokenRejectTransaction, KeyList.of(owner.getPrivateKey()));
        log.info("Owner {} rejected NFT tokens {} via {}", owner, nftIds, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse executeFungibleTokenAirdrop(
            TokenId tokenId, ExpandedAccountId sender, AccountId recipient, long amount) {
        var transaction = getFungibleTokenAirdropTransaction(tokenId, sender.getAccountId(), recipient, amount);
        var response = executeTransactionAndRetrieveReceipt(transaction, KeyList.of(sender.getPrivateKey()), sender);
        log.info(
                "Airdropped {} tokens of {} from {} to {} via {}",
                amount,
                tokenId,
                sender,
                recipient,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse executeNftAirdrop(
            TokenId tokenId, ExpandedAccountId sender, ExpandedAccountId receiver, long serialNumber) {
        var transaction =
                getNftAirdropTransaction(tokenId, sender.getAccountId(), receiver.getAccountId(), serialNumber);
        var response = executeTransactionAndRetrieveReceipt(transaction, KeyList.of(sender.getPrivateKey()), sender);
        log.info(
                "Airdropped serial numbers {} of {} from {} to {} via {}",
                serialNumber,
                tokenId,
                sender,
                receiver,
                response.getTransactionId());
        return response;
    }

    private TokenAirdropTransaction getFungibleTokenAirdropTransaction(
            TokenId tokenId, AccountId sender, AccountId recipient, long amount) {
        var airdropTransaction = new TokenAirdropTransaction().setTransactionMemo(getMemo("Airdrop token"));

        airdropTransaction.addTokenTransfer(tokenId, sender, Math.negateExact(amount));
        airdropTransaction.addTokenTransfer(tokenId, recipient, amount);

        return airdropTransaction;
    }

    public NetworkTransactionResponse executeCancelTokenAirdrop(
            ExpandedAccountId sender, AccountId receiver, TokenId tokenId) {
        var pendingAirdropIds = getPendingAirdropIds(sender.getAccountId(), receiver, tokenId);
        var transaction = getCancelTokenAirdropTransaction(pendingAirdropIds);
        var response = executeTransactionAndRetrieveReceipt(transaction, KeyList.of(sender.getPrivateKey()), sender);
        log.info("Cancel pending Airdrop from {} to {} via {}", sender, receiver, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse executeCancelNftAirdrop(
            ExpandedAccountId sender, AccountId receiver, NftId nftId) {

        var pendingAirdropIds = getPendingNftAirdropIds(sender.getAccountId(), receiver, nftId);
        var transaction = getCancelTokenAirdropTransaction(pendingAirdropIds);
        var response = executeTransactionAndRetrieveReceipt(transaction, KeyList.of(sender.getPrivateKey()), sender);
        log.info(
                "Cancel pending Airdrop for {} from {} to {} via {}",
                nftId,
                sender,
                receiver,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse executeClaimTokenAirdrop(
            ExpandedAccountId sender, ExpandedAccountId receiver, TokenId tokenId) {
        var pendingAirdropIds = getPendingAirdropIds(sender.getAccountId(), receiver.getAccountId(), tokenId);
        var transaction = getClaimTokenAirdropsTransaction(pendingAirdropIds);
        var response =
                executeTransactionAndRetrieveReceipt(transaction, KeyList.of(receiver.getPrivateKey()), receiver);
        log.info("Claim pending Airdrop from {} to {} via {}", sender, receiver, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse executeClaimNftAirdrop(
            ExpandedAccountId sender, ExpandedAccountId receiver, NftId nftId) {

        var pendingAirdropIds = getPendingNftAirdropIds(sender.getAccountId(), receiver.getAccountId(), nftId);
        var transaction = getClaimTokenAirdropsTransaction(pendingAirdropIds);
        var response =
                executeTransactionAndRetrieveReceipt(transaction, KeyList.of(receiver.getPrivateKey()), receiver);
        log.info(
                "Claim pending Airdrop for {} from {} to {} via {}",
                nftId,
                sender,
                receiver,
                response.getTransactionId());
        return response;
    }

    private PendingAirdropId getPendingAirdropIds(AccountId sender, AccountId receiver, TokenId tokenId) {
        return new PendingAirdropId().setSender(sender).setReceiver(receiver).setTokenId(tokenId);
    }

    private PendingAirdropId getPendingNftAirdropIds(AccountId sender, AccountId receiver, NftId nftId) {
        return new PendingAirdropId().setSender(sender).setReceiver(receiver).setNftId(nftId);
    }

    private TokenCancelAirdropTransaction getCancelTokenAirdropTransaction(PendingAirdropId pendingAirdropId) {
        return new TokenCancelAirdropTransaction()
                .setTransactionMemo("Cancel token airdrop")
                .addPendingAirdrop(pendingAirdropId);
    }

    private TokenClaimAirdropTransaction getClaimTokenAirdropsTransaction(PendingAirdropId pendingAirdropId) {
        return new TokenClaimAirdropTransaction()
                .setTransactionMemo("Claim token airdrop")
                .addPendingAirdrop(pendingAirdropId);
    }

    private TokenAirdropTransaction getNftAirdropTransaction(
            TokenId tokenId, AccountId sender, AccountId recipient, long serialNumber) {
        var airdropTransaction = new TokenAirdropTransaction().setTransactionMemo(getMemo("Airdrop Nft"));
        var nftId = new NftId(tokenId, serialNumber);
        return airdropTransaction.addNftTransfer(nftId, sender, recipient);
    }

    @RequiredArgsConstructor
    @Getter
    public enum TokenNameEnum {
        // also used in call.feature
        FUNGIBLE(
                "fungible",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.Unfrozen,
                false),
        FUNGIBLEHISTORICAL(
                "fungible_historical",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen,
                false),
        FUNGIBLE_DELETABLE(
                "fungible_deletable",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                false),
        FUNGIBLE_FOR_ETH_CALL(
                "fungible",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                false),
        // used in tokenAllowance.feature and not using snake_case because cucumber cannot detect the enum
        ALLOWANCEFUNGIBLE(
                "allowance_fungible",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                false),
        FUNGIBLE_WITH_CUSTOM_FEES(
                "fungible_custom_fees",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                false),
        ALLOWANCE_NON_FUNGIBLE(
                "allowance_non_fungible",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                false),
        NFT(
                "non_fungible",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.Unfrozen,
                false),
        NFT_FOR_ETH_CALL(
                "non_fungible",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                false),
        NFT_ERC(
                "non_fungible_erc",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                false),
        NFTHISTORICAL(
                "non_fungible_historical",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen,
                false),
        NFT_AIRDROP(
                "non_fungible_airdrop",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                true),
        NFT_DELETABLE(
                "non_fungible_deletable",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable,
                true),
        FUNGIBLE_KYC_UNFROZEN(
                "fungible_kyc_unfrozen",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen,
                false),
        FUNGIBLE_KYC_UNFROZEN_2(
                "fungible_kyc_unfrozen_2",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen,
                true),
        FUNGIBLE_KYC_UNFROZEN_FOR_ETH_CALL(
                "fungible_kyc_unfrozen_for_eth_call",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen,
                false),
        FUNGIBLE_KYC_NOT_APPLICABLE_UNFROZEN(
                "fungible_kyc_not_applicable_unfrozen",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.Unfrozen,
                false),
        FUNGIBLE_AIRDROP(
                "fungible_airdrop",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.Unfrozen,
                false),
        NFT_KYC_NOT_APPLICABLE_UNFROZEN(
                "nft_kyc_not_applicable_unfrozen",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.Unfrozen,
                false),
        NFT_KYC_UNFROZEN(
                "nft_kyc_unfrozen",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen,
                false),
        FUNGIBLE_FOR_CUSTOM_FEE(
                "fungible_for_custom_fee",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen,
                false);

        private final String symbol;
        private final TokenType tokenType;
        private final TokenKycStatus tokenKycStatus;
        private final TokenFreezeStatus tokenFreezeStatus;
        private final boolean createWithMetadata;

        @Override
        public String toString() {
            return String.format("%s (%s)", symbol, tokenType);
        }
    }

    @Builder(toBuilder = true)
    public record TokenResponse(
            TokenId tokenId, NetworkTransactionResponse response, PrivateKey metadataKey, byte[] metadata) {}

    private record TokenAccount(TokenId tokenId, ExpandedAccountId accountId) {}
}
