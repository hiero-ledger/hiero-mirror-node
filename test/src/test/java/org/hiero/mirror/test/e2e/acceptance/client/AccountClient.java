// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountAllowanceApproveTransaction;
import com.hedera.hashgraph.sdk.AccountAllowanceDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.EvmAddress;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.Key;
import com.hedera.hashgraph.sdk.proto.Key.KeyCase;
import jakarta.inject.Named;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.retry.support.RetryTemplate;

@CustomLog
@Named
public class AccountClient extends AbstractNetworkClient {

    private final Map<AccountNameEnum, ExpandedAccountId> accountMap = new ConcurrentHashMap<>();
    private final Collection<ExpandedAccountId> accountIds = new CopyOnWriteArrayList<>();
    private final long initialBalance;

    public AccountClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        super(sdkClient, retryTemplate, acceptanceTestProperties);
        try {
            initialBalance = getBalance();
            log.info(
                    "Operator account {} initial balance is {}",
                    sdkClient.getExpandedOperatorAccountId(),
                    initialBalance);
        } catch (Throwable t) {
            clean();
            throw t;
        }
    }

    @Override
    public void clean() {
        log.info("Deleting {} accounts", accountIds.size());
        deleteOrLogEntities(accountIds, this::delete);

        var cost = initialBalance - getBalance();
        log.warn("Tests cost {} to run", Hbar.fromTinybars(cost));

        var operatorId = sdkClient.getDefaultOperator();
        var tempOperatorId = sdkClient.getExpandedOperatorAccountId();

        if (!operatorId.equals(tempOperatorId)) {
            try {
                delete(tempOperatorId);
            } catch (Exception e) {
                // ignore
            }
        }

        client.setOperator(operatorId.getAccountId(), operatorId.getPrivateKey());
    }

    @Override
    protected void logEntities() {
        for (var accountName : accountMap.keySet()) {
            // Log the values so that they can be parsed in CI and passed to the k6 tests as input.
            System.out.println(accountName + "="
                    + accountMap.get(accountName).getAccountId().toEvmAddress());
            System.out.println("DEFAULT_ACCOUNT_ID_" + accountName + "="
                    + accountMap.get(accountName).getAccountId());
            System.out.println("DEFAULT_ACCOUNT_ID_KEY_" + accountName + "="
                    + accountMap.get(accountName).getPublicKey().toStringRaw());
        }
    }

    @Override
    public int getOrder() {
        return 1; // Run cleanup last so it prints cost
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId) {
        var operatorId = sdkClient.getDefaultOperator().getAccountId();

        try {
            var accountDeleteTransaction = new AccountDeleteTransaction()
                    .setAccountId(accountId.getAccountId())
                    .setTransferAccountId(operatorId)
                    .freezeWith(client)
                    .sign(accountId.getPrivateKey());
            var response = executeTransactionAndRetrieveReceipt(accountDeleteTransaction);
            log.info("Deleted account {} via {}", accountId, response.getTransactionId());
            return response;
        } catch (Exception e) {
            try {
                log.warn(
                        "Unable to delete account {}. Manually transferring remaining funds to operator: {}",
                        accountId,
                        e.getMessage());
                var amount = Hbar.fromTinybars(getBalance(accountId) - 100_000L);
                sendCryptoTransfer(accountId, operatorId, amount, accountId.getPrivateKey());
            } catch (Exception fallbackException) {
                log.error("Fallback transfer failed: ", fallbackException);
            }
            throw e;
        } finally {
            accountIds.remove(accountId);
            accountMap.values().remove(accountId);
        }
    }

    public ExpandedAccountId getAccount(AccountNameEnum accountNameEnum) {
        if (accountNameEnum == AccountNameEnum.OPERATOR) {
            return sdkClient.getExpandedOperatorAccountId();
        }

        ExpandedAccountId accountId = accountMap.computeIfAbsent(accountNameEnum, x -> {
            try {
                return createNewAccount(acceptanceTestProperties.getChildAccountBalance(), accountNameEnum);
            } catch (Exception e) {
                log.warn("Issue creating additional account: {}, ex: {}", accountNameEnum, e);
                return null;
            }
        });

        if (accountId == null) {
            throw new NetworkException("Null accountId retrieved from receipt");
        }

        return accountId;
    }

    public TransferTransaction getCryptoTransferTransaction(AccountId sender, AccountId recipient, Hbar hbarAmount) {
        return new TransferTransaction()
                .addHbarTransfer(sender, hbarAmount.negated())
                .addHbarTransfer(recipient, hbarAmount)
                .setTransactionMemo(getMemo("Crypto transfer"));
    }

    public NetworkTransactionResponse sendApprovedCryptoTransfer(
            ExpandedAccountId spender, AccountId recipient, Hbar hbarAmount) {
        var transferTransaction = new TransferTransaction()
                .addApprovedHbarTransfer(getClient().getOperatorAccountId(), hbarAmount.negated())
                .addHbarTransfer(recipient, hbarAmount)
                .setTransactionMemo(getMemo("Approved transfer"));
        var response = executeTransactionAndRetrieveReceipt(transferTransaction, spender);
        log.info(
                "Approved transfer {} from {} to {} via {}",
                hbarAmount,
                spender,
                recipient,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse sendCryptoTransfer(AccountId recipient, Hbar hbarAmount, PrivateKey privateKey) {
        return sendCryptoTransfer(sdkClient.getExpandedOperatorAccountId(), recipient, hbarAmount, privateKey);
    }

    private NetworkTransactionResponse sendCryptoTransfer(
            ExpandedAccountId sender, AccountId recipient, Hbar hbarAmount, PrivateKey privateKey) {
        var cryptoTransferTransaction = getCryptoTransferTransaction(sender.getAccountId(), recipient, hbarAmount);
        var response = executeTransactionAndRetrieveReceipt(
                cryptoTransferTransaction, privateKey == null ? null : KeyList.of(privateKey), sender);
        log.info("Transferred {} from {} to {} via {}", hbarAmount, sender, recipient, response.getTransactionId());
        return response;
    }

    public AccountCreateTransaction getAccountCreateTransaction(
            Hbar initialBalance, KeyList publicKeys, boolean receiverSigRequired, String customMemo, EvmAddress alias) {
        String memo = getMemo(String.format("%s %s ", "Create Crypto Account", customMemo));
        return new AccountCreateTransaction()
                .setInitialBalance(initialBalance)
                // The only _required_ property here is `key`
                .setKeyWithoutAlias(publicKeys)
                .setAlias(alias)
                .setAccountMemo(memo)
                .setReceiverSignatureRequired(receiverSigRequired)
                .setTransactionMemo(memo);
    }

    public ExpandedAccountId createNewAccount(long initialBalance) {
        // By default, use ALICE's key type if not specified->ED25519
        Key.KeyCase keyType = AccountNameEnum.ALICE.keyType;
        return createCryptoAccount(Hbar.fromTinybars(initialBalance), false, null, null, keyType);
    }

    public ExpandedAccountId createNewAccount(final BigDecimal initialBalance, final AccountNameEnum accountNameEnum) {
        // Get the keyType from the enum
        Key.KeyCase keyType = accountNameEnum.keyType;
        return createCryptoAccount(
                sdkClient.convert(initialBalance),
                accountNameEnum.receiverSigRequired,
                null,
                accountNameEnum.name(),
                keyType);
    }

    private ExpandedAccountId createCryptoAccount(
            Hbar initialBalance, boolean receiverSigRequired, KeyList keyList, String memo, Key.KeyCase keyType) {
        // Depending on keyType, generate an Ed25519 or ECDSA private, public key pair
        PrivateKey privateKey;
        PublicKey publicKey;
        if (keyType == Key.KeyCase.ED25519) {
            privateKey = PrivateKey.generateED25519();
            publicKey = privateKey.getPublicKey();
        } else if (keyType == Key.KeyCase.ECDSA_SECP256K1) {
            privateKey = PrivateKey.generateECDSA();
            publicKey = privateKey.getPublicKey();
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + keyType);
        }

        log.trace("Private key = {}", privateKey);
        log.trace("Public key = {}", publicKey);

        KeyList publicKeyList = KeyList.of(privateKey.getPublicKey());
        if (keyList != null) {
            publicKeyList.addAll(keyList);
        }

        AccountId newAccountId;
        NetworkTransactionResponse response;
        final boolean isED25519 = keyType == KeyCase.ED25519;
        Transaction<?> transaction = getAccountCreateTransaction(
                initialBalance,
                publicKeyList,
                receiverSigRequired,
                memo == null ? "" : memo,
                isED25519 ? null : privateKey.getPublicKey().toEvmAddress());
        var keys = receiverSigRequired || keyType == KeyCase.ECDSA_SECP256K1 ? KeyList.of(privateKey) : null;
        response = executeTransactionAndRetrieveReceipt(transaction, keys);
        TransactionReceipt receipt = response.getReceipt();
        newAccountId = receipt.accountId;
        if (receipt.accountId == null) {
            throw new NetworkException(String.format(
                    "Receipt for %s returned no accountId, receipt: %s", response.getTransactionId(), receipt));
        }

        var accountName = AccountNameEnum.of(memo).map(a -> a + " ").orElse("");
        log.info(
                "Created new {} account {}{} with {} via {}",
                keyType,
                accountName,
                newAccountId,
                initialBalance,
                response.getTransactionId());
        var accountId = new ExpandedAccountId(newAccountId, privateKey);
        accountIds.add(accountId);
        return accountId;
    }

    public NetworkTransactionResponse approveCryptoAllowance(AccountId spender, Hbar hbarAmount) {
        var transaction = new AccountAllowanceApproveTransaction().approveHbarAllowance(null, spender, hbarAmount);
        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info("Approved spender {} an allowance of {} via {}", spender, hbarAmount, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse approveNft(NftId nftId, AccountId spender) {
        var ownerAccountId = sdkClient.getExpandedOperatorAccountId().getAccountId();
        var transaction =
                new AccountAllowanceApproveTransaction().approveTokenNftAllowance(nftId, ownerAccountId, spender);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Approved spender {} a NFT allowance on {} and serial {} via {}",
                spender,
                nftId.tokenId,
                nftId.serial,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse approveToken(TokenId tokenId, AccountId spender, long amount) {
        var ownerAccountId = sdkClient.getExpandedOperatorAccountId().getAccountId();
        var transaction = new AccountAllowanceApproveTransaction()
                .approveTokenAllowance(tokenId, ownerAccountId, spender, amount);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Approved spender {} a token allowance on {} of {} via {}",
                spender,
                tokenId,
                amount,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse approveNftAllSerials(TokenId tokenId, AccountId spender) {
        var ownerAccountId = sdkClient.getExpandedOperatorAccountId().getAccountId();
        var transaction = new AccountAllowanceApproveTransaction()
                .approveTokenNftAllowanceAllSerials(tokenId, ownerAccountId, spender);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Approved spender {} an allowance for all serial numbers on {} via {}",
                spender,
                tokenId,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteAllowanceForNft(ExpandedAccountId spender, NftId nftId) {
        var transaction =
                new AccountAllowanceDeleteTransaction().deleteAllTokenNftAllowances(nftId, spender.getAccountId());
        var response = executeTransactionAndRetrieveReceipt(transaction, KeyList.of(spender.getPrivateKey()));
        log.info(
                "Deleted allowance for spender {} on NFT {} for serial {} via {}",
                spender,
                nftId.tokenId,
                nftId.serial,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse approveNftAllSerials(TokenId tokenId, ContractId spender)
            throws InvalidProtocolBufferException {
        var ownerAccountId = sdkClient.getExpandedOperatorAccountId().getAccountId();
        var transaction = new AccountAllowanceApproveTransaction()
                .approveTokenNftAllowanceAllSerials(tokenId, ownerAccountId, AccountId.fromBytes(spender.toBytes()));
        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Approved spender {} an allowance for all serial numbers on {} via {}",
                spender,
                tokenId,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteNftAllowance(TokenId tokenId, AccountId owner, AccountId spender) {

        var transaction =
                new AccountAllowanceApproveTransaction().deleteTokenNftAllowanceAllSerials(tokenId, owner, spender);
        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Deleted allowance for owner {} and spender {} on {} via {}",
                owner,
                spender,
                tokenId,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse updateAccount(
            ExpandedAccountId accountId, Consumer<AccountUpdateTransaction> transaction) {
        final var accountUpdateTransaction = new AccountUpdateTransaction();
        transaction.accept(accountUpdateTransaction);
        accountUpdateTransaction
                .setAccountId(accountId.getAccountId())
                .freezeWith(client)
                .sign(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(accountUpdateTransaction);
        log.info(" account updated via {}", response.getTransactionId());
        return response;
    }

    @RequiredArgsConstructor
    public enum AccountNameEnum {
        ALICE(false, Key.KeyCase.ED25519),
        BOB(true, Key.KeyCase.ECDSA_SECP256K1),
        // used in token.feature
        CAROL(false, Key.KeyCase.ED25519),
        DAVE(false, Key.KeyCase.ED25519),
        DELETABLE(false, KeyCase.ED25519),
        OPERATOR(false, Key.KeyCase.ED25519), // These may not be accurate for operator
        TOKEN_TREASURY(false, Key.KeyCase.ED25519);

        private final boolean receiverSigRequired;
        private final Key.KeyCase keyType;

        static Optional<AccountNameEnum> of(String name) {
            try {
                return Optional.ofNullable(name).map(AccountNameEnum::valueOf);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }
}
