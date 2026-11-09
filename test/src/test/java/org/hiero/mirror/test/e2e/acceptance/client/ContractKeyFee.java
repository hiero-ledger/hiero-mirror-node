// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.proto.ConsensusCreateTopicTransactionBody;
import com.hedera.hashgraph.sdk.proto.ConsensusUpdateTopicTransactionBody;
import com.hedera.hashgraph.sdk.proto.ContractCreateTransactionBody;
import com.hedera.hashgraph.sdk.proto.ContractUpdateTransactionBody;
import com.hedera.hashgraph.sdk.proto.CryptoCreateTransactionBody;
import com.hedera.hashgraph.sdk.proto.CryptoUpdateTransactionBody;
import com.hedera.hashgraph.sdk.proto.FileCreateTransactionBody;
import com.hedera.hashgraph.sdk.proto.FileUpdateTransactionBody;
import com.hedera.hashgraph.sdk.proto.Key;
import com.hedera.hashgraph.sdk.proto.ScheduleCreateTransactionBody;
import com.hedera.hashgraph.sdk.proto.SignedTransaction;
import com.hedera.hashgraph.sdk.proto.TokenCreateTransactionBody;
import com.hedera.hashgraph.sdk.proto.TokenUpdateTransactionBody;
import com.hedera.hashgraph.sdk.proto.Transaction;
import com.hedera.hashgraph.sdk.proto.TransactionBody;
import com.hedera.hashgraph.sdk.proto.TransactionList;
import java.util.List;

/**
 * Extra KEYS fee a consensus node charges for a contract id key.
 *
 * <p>{@code FeeKeyUtils} in consensus node 0.78 counts only cryptographic keys. Nodes that include consensus-node
 * change #27590 also count {@code CONTRACT_ID} and {@code DELEGATABLE_CONTRACT_ID}. The fee endpoint uses the 0.78
 * calculator, so its estimate omits those keys. This is the tinycents a newer node adds on top of that estimate, using
 * the genesis simple-fee schedule: a KEYS unit is 100,000,000 tinycents and one key is included, except consensus
 * topic create, which includes none.
 */
final class ContractKeyFee {

    private static final long KEYS_FEE_TINYCENTS = 100_000_000L;

    private ContractKeyFee() {}

    static long extraTinycents(final byte[] transactionListBytes) {
        try {
            final var transactions = TransactionList.parseFrom(transactionListBytes);
            if (transactions.getTransactionListCount() == 0) {
                return 0L;
            }
            return extraTinycents(body(transactions.getTransactionList(0)));
        } catch (InvalidProtocolBufferException e) {
            return 0L;
        }
    }

    private static TransactionBody body(final Transaction transaction) throws InvalidProtocolBufferException {
        if (!transaction.getSignedTransactionBytes().isEmpty()) {
            final var signed = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
            return TransactionBody.parseFrom(signed.getBodyBytes());
        }
        return TransactionBody.parseFrom(transaction.getBodyBytes());
    }

    private static long extraTinycents(final TransactionBody body) {
        final var included = includedKeys(body.getDataCase());
        if (included == Long.MAX_VALUE) {
            return 0L;
        }
        final var keyCounter = countKeys(body);
        final var pricedByEstimator = Math.max(0L, keyCounter.cryptographic - included);
        final var pricedByCurrentNode = Math.max(0L, keyCounter.cryptographic + keyCounter.contract - included);
        return Math.multiplyExact(pricedByCurrentNode - pricedByEstimator, KEYS_FEE_TINYCENTS);
    }

    /**
     * Keys included in the service base fee. {@link Long#MAX_VALUE} means the transaction has no KEYS extra.
     */
    private static long includedKeys(final TransactionBody.DataCase dataCase) {
        return switch (dataCase) {
            case CONSENSUSCREATETOPIC -> 0L;
            case CONSENSUSUPDATETOPIC,
                    CONTRACTCREATEINSTANCE,
                    CONTRACTUPDATEINSTANCE,
                    CRYPTOCREATEACCOUNT,
                    CRYPTOUPDATEACCOUNT,
                    FILECREATE,
                    FILEUPDATE,
                    SCHEDULECREATE,
                    TOKENCREATION,
                    TOKENUPDATE -> 1L;
            default -> Long.MAX_VALUE;
        };
    }

    private static KeyCounter countKeys(final TransactionBody body) {
        return switch (body.getDataCase()) {
            case CONSENSUSCREATETOPIC -> countKeys(body.getConsensusCreateTopic());
            case CONSENSUSUPDATETOPIC -> countKeys(body.getConsensusUpdateTopic());
            case CONTRACTCREATEINSTANCE -> countKeys(body.getContractCreateInstance());
            case CONTRACTUPDATEINSTANCE -> countKeys(body.getContractUpdateInstance());
            case CRYPTOCREATEACCOUNT -> countKeys(body.getCryptoCreateAccount());
            case CRYPTOUPDATEACCOUNT -> countKeys(body.getCryptoUpdateAccount());
            case FILECREATE -> countKeys(body.getFileCreate());
            case FILEUPDATE -> countKeys(body.getFileUpdate());
            case SCHEDULECREATE -> countKeys(body.getScheduleCreate());
            case TOKENCREATION -> countKeys(body.getTokenCreation());
            case TOKENUPDATE -> countKeys(body.getTokenUpdate());
            default -> KeyCounter.NONE;
        };
    }

    private static KeyCounter countKeys(final ConsensusCreateTopicTransactionBody body) {
        return countKeys(body.hasAdminKey(), body.getAdminKey())
                .plus(countKeys(body.hasFeeScheduleKey(), body.getFeeScheduleKey()))
                .plus(countKeys(body.hasSubmitKey(), body.getSubmitKey()));
    }

    private static KeyCounter countKeys(final ConsensusUpdateTopicTransactionBody body) {
        var keyCounter = countKeys(body.hasAdminKey(), body.getAdminKey())
                .plus(countKeys(body.hasFeeScheduleKey(), body.getFeeScheduleKey()))
                .plus(countKeys(body.hasSubmitKey(), body.getSubmitKey()));
        if (body.hasFeeExemptKeyList()) {
            keyCounter = keyCounter.plus(countKeys(body.getFeeExemptKeyList().getKeysList()));
        }
        return keyCounter;
    }

    private static KeyCounter countKeys(final ContractCreateTransactionBody body) {
        return countKeys(body.hasAdminKey(), body.getAdminKey());
    }

    private static KeyCounter countKeys(final ContractUpdateTransactionBody body) {
        return countKeys(body.hasAdminKey(), body.getAdminKey());
    }

    private static KeyCounter countKeys(final CryptoCreateTransactionBody body) {
        return countKeys(body.hasKey(), body.getKey());
    }

    private static KeyCounter countKeys(final CryptoUpdateTransactionBody body) {
        return countKeys(body.hasKey(), body.getKey());
    }

    private static KeyCounter countKeys(final FileCreateTransactionBody body) {
        return body.hasKeys() ? countKeys(body.getKeys().getKeysList()) : KeyCounter.NONE;
    }

    private static KeyCounter countKeys(final FileUpdateTransactionBody body) {
        return body.hasKeys() ? countKeys(body.getKeys().getKeysList()) : KeyCounter.NONE;
    }

    private static KeyCounter countKeys(final ScheduleCreateTransactionBody body) {
        return countKeys(body.hasAdminKey(), body.getAdminKey());
    }

    private static KeyCounter countKeys(final TokenCreateTransactionBody body) {
        return tokenKeys(body);
    }

    private static KeyCounter countKeys(final TokenUpdateTransactionBody body) {
        return tokenKeys(body);
    }

    private static KeyCounter tokenKeys(final TokenCreateTransactionBody body) {
        return countKeys(body.hasAdminKey(), body.getAdminKey())
                .plus(countKeys(body.hasFeeScheduleKey(), body.getFeeScheduleKey()))
                .plus(countKeys(body.hasFreezeKey(), body.getFreezeKey()))
                .plus(countKeys(body.hasKycKey(), body.getKycKey()))
                .plus(countKeys(body.hasMetadataKey(), body.getMetadataKey()))
                .plus(countKeys(body.hasPauseKey(), body.getPauseKey()))
                .plus(countKeys(body.hasSupplyKey(), body.getSupplyKey()))
                .plus(countKeys(body.hasWipeKey(), body.getWipeKey()));
    }

    private static KeyCounter tokenKeys(final TokenUpdateTransactionBody body) {
        return countKeys(body.hasAdminKey(), body.getAdminKey())
                .plus(countKeys(body.hasFeeScheduleKey(), body.getFeeScheduleKey()))
                .plus(countKeys(body.hasFreezeKey(), body.getFreezeKey()))
                .plus(countKeys(body.hasKycKey(), body.getKycKey()))
                .plus(countKeys(body.hasMetadataKey(), body.getMetadataKey()))
                .plus(countKeys(body.hasPauseKey(), body.getPauseKey()))
                .plus(countKeys(body.hasSupplyKey(), body.getSupplyKey()))
                .plus(countKeys(body.hasWipeKey(), body.getWipeKey()));
    }

    private static KeyCounter countKeys(final boolean present, final Key key) {
        return present ? countKeys(key) : KeyCounter.NONE;
    }

    private static KeyCounter countKeys(final List<Key> keys) {
        var keyCounter = KeyCounter.NONE;
        for (final var key : keys) {
            keyCounter = keyCounter.plus(countKeys(key));
        }
        return keyCounter;
    }

    private static KeyCounter countKeys(final Key key) {
        return switch (key.getKeyCase()) {
            case ED25519, ECDSA_384, ECDSA_SECP256K1 -> new KeyCounter(1, 0);
            case CONTRACTID, DELEGATABLE_CONTRACT_ID -> new KeyCounter(0, 1);
            case THRESHOLDKEY -> countKeys(key.getThresholdKey().getKeys().getKeysList());
            case KEYLIST -> countKeys(key.getKeyList().getKeysList());
            default -> KeyCounter.NONE;
        };
    }

    /**
     * Counts the keys a transaction sets, split by whether the 0.78 fee calculator prices them.
     *
     * <p>{@code cryptographic} is an ED25519 or ECDSA key. The fee endpoint and the consensus node both count these.
     * {@code contract} is a contract id or delegatable contract id. The 0.78 calculator ignores these, and a node that
     * includes consensus-node change #27590 counts each one. A threshold or key list contributes the counts of the
     * keys inside it.
     *
     * @param cryptographic number of cryptographic keys
     * @param contract number of contract id keys
     */
    private record KeyCounter(long cryptographic, long contract) {
        private static final KeyCounter NONE = new KeyCounter(0, 0);

        private KeyCounter plus(final KeyCounter other) {
            return new KeyCounter(cryptographic + other.cryptographic, contract + other.contract);
        }
    }
}
