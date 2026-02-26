// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.addressbook.NodeCreateTransactionBody;
import com.hedera.hapi.node.addressbook.NodeDeleteTransactionBody;
import com.hedera.hapi.node.addressbook.NodeUpdateTransactionBody;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractDeleteTransactionBody;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.file.FileDeleteTransactionBody;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.token.TokenCancelAirdropTransactionBody;
import com.hedera.hapi.node.token.TokenClaimAirdropTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.token.TokenDeleteTransactionBody;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.token.TokenRejectTransactionBody;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateNftsTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.AtomicBatchTransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.repository.FileDataRepository;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
final class FeeEstimationServiceTest extends RestJavaIntegrationTest {

    private final FeeEstimationService service;
    private final FileDataRepository fileDataRepository;
    private final SystemEntity systemEntity;

    // Fee schedule loaded from the same bundled JSON the service falls back to.
    // All expected fee values are derived from this schedule — no hardcoded numbers.
    private static final FeeSchedule FEE_SCHEDULE = loadFeeSchedule();

    // Node fee components
    private static final long NODE_BASE_FEE = FEE_SCHEDULE.node().baseFee();
    private static final int NETWORK_MULTIPLIER = FEE_SCHEDULE.network().multiplier();

    // For a DEFAULT transaction (no signatures, body under the included-bytes threshold):
    // total = nodeBase × (1 + networkMultiplier)
    private static final long NODE_PORTION = NODE_BASE_FEE + NODE_BASE_FEE * NETWORK_MULTIPLIER;
    private static final long KEY_FEE = extraFee(Extra.KEYS);
    private static final int SIGNATURES_INCLUDED = FEE_SCHEDULE.node().extras().stream()
            .filter(e -> e.name() == Extra.SIGNATURES)
            .findFirst()
            .orElseThrow()
            .includedCount();
    private static final long SIGNATURES_FEE = extraFee(Extra.SIGNATURES);
    private static final long STATE_BYTES_FEE = extraFee(Extra.STATE_BYTES);
    private static final long TOKEN_MINT_NFT_FEE = extraFee(Extra.TOKEN_MINT_NFT);
    private static final long TOKEN_MINT_NFT_BASE_FEE = extraFee(Extra.TOKEN_MINT_NFT_BASE);
    private static final long ALLOWANCE_FEE = extraFee(Extra.ALLOWANCES);
    private static final long CONSENSUS_SUBMIT_MESSAGE_FEE = serviceFee(HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE);
    private static final long CRYPTO_DELETE_FEE = serviceFee(HederaFunctionality.CRYPTO_DELETE);
    private static final long CRYPTO_CREATE_FEE = serviceFee(HederaFunctionality.CRYPTO_CREATE);
    private static final int ED25519_KEY_SIZE = 32;
    private static final int ED25519_SIGNATURE_SIZE = 64;
    private static final int INVALID_TX_SIZE = 100;
    private static final int LONG_MESSAGE_BYTES = 2_000;
    private static final int SHORT_MESSAGE_BYTES = 100;
    private static final long DELETE_ACCOUNT_NUM = 1001L;
    private static final long TOPIC_NUM = 1L;
    private static final long TRANSFER_ACCOUNT_NUM = 3L;
    // service: CONSENSUS_SUBMIT_MESSAGE_FEE + (LONG_MESSAGE_BYTES - 1_024) × STATE_BYTES_FEE = 983_000_000
    // node:    NODE_PORTION + processing-bytes charge (varies with exact serialized body size) = 100_300_000
    private static final long LONG_MESSAGE_SUBMIT_TOTAL =
            CONSENSUS_SUBMIT_MESSAGE_FEE + (long) (LONG_MESSAGE_BYTES - 1_024) * STATE_BYTES_FEE + 100_300_000L;

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTransactionTypes")
    void estimatesFeeForTransactionType(FeeCase c) {
        var result = service.estimateFees(buildTransaction(c.body()), FeeEstimateMode.INTRINSIC);
        assertThat(result.totalTinycents()).isEqualTo(c.total());
    }

    static Stream<Named<FeeCase>> allTransactionTypes() {
        return Stream.of(
                        // Consensus
                        fee(
                                b -> b.consensusCreateTopic(ConsensusCreateTopicTransactionBody.DEFAULT),
                                HederaFunctionality.CONSENSUS_CREATE_TOPIC),
                        fee(
                                b -> b.consensusDeleteTopic(ConsensusDeleteTopicTransactionBody.DEFAULT),
                                HederaFunctionality.CONSENSUS_DELETE_TOPIC),
                        fee(
                                b -> b.consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT),
                                HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE),
                        fee(
                                b -> b.consensusUpdateTopic(ConsensusUpdateTopicTransactionBody.DEFAULT),
                                HederaFunctionality.CONSENSUS_UPDATE_TOPIC),
                        // Contract
                        fee(
                                b -> b.contractCreateInstance(ContractCreateTransactionBody.DEFAULT),
                                HederaFunctionality.CONTRACT_CREATE),
                        fee(
                                b -> b.contractDeleteInstance(ContractDeleteTransactionBody.DEFAULT),
                                HederaFunctionality.CONTRACT_DELETE),
                        fee(
                                b -> b.contractUpdateInstance(ContractUpdateTransactionBody.DEFAULT),
                                HederaFunctionality.CONTRACT_UPDATE),
                        fee(
                                b -> b.ethereumTransaction(EthereumTransactionBody.DEFAULT),
                                HederaFunctionality.ETHEREUM_TRANSACTION),
                        // Crypto
                        // 2 crypto + 6 NFT + 2 token = 10 allowances; 1 included, 9 charged at ALLOWANCE_FEE
                        fee(
                                b -> b.cryptoApproveAllowance(cryptoApproveAllowanceBody()),
                                NODE_PORTION
                                        + serviceFee(HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE)
                                        + 9 * ALLOWANCE_FEE),
                        fee(
                                b -> b.cryptoCreateAccount(CryptoCreateTransactionBody.DEFAULT),
                                HederaFunctionality.CRYPTO_CREATE),
                        fee(
                                b -> b.cryptoDelete(CryptoDeleteTransactionBody.DEFAULT),
                                HederaFunctionality.CRYPTO_DELETE),
                        fee(
                                b -> b.cryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.DEFAULT),
                                HederaFunctionality.CRYPTO_DELETE_ALLOWANCE),
                        fee(
                                b -> b.cryptoTransfer(CryptoTransferTransactionBody.DEFAULT),
                                HederaFunctionality.CRYPTO_TRANSFER),
                        fee(
                                b -> b.cryptoUpdateAccount(CryptoUpdateTransactionBody.DEFAULT),
                                HederaFunctionality.CRYPTO_UPDATE),
                        // File
                        fee(b -> b.fileAppend(FileAppendTransactionBody.DEFAULT), HederaFunctionality.FILE_APPEND),
                        fee(b -> b.fileCreate(FileCreateTransactionBody.DEFAULT), HederaFunctionality.FILE_CREATE),
                        fee(b -> b.fileDelete(FileDeleteTransactionBody.DEFAULT), HederaFunctionality.FILE_DELETE),
                        fee(b -> b.fileUpdate(FileUpdateTransactionBody.DEFAULT), HederaFunctionality.FILE_UPDATE),
                        // Node
                        fee(b -> b.nodeCreate(NodeCreateTransactionBody.DEFAULT), HederaFunctionality.NODE_CREATE),
                        fee(b -> b.nodeDelete(NodeDeleteTransactionBody.DEFAULT), HederaFunctionality.NODE_DELETE),
                        fee(b -> b.nodeUpdate(NodeUpdateTransactionBody.DEFAULT), HederaFunctionality.NODE_UPDATE),
                        // Schedule
                        fee(
                                b -> b.scheduleDelete(ScheduleDeleteTransactionBody.DEFAULT),
                                HederaFunctionality.SCHEDULE_DELETE),
                        fee(
                                b -> b.scheduleSign(ScheduleSignTransactionBody.DEFAULT),
                                HederaFunctionality.SCHEDULE_SIGN),
                        // Token
                        // 1 fungible + 1 NFT transfer (2 token lists); calculator charges NODE_PORTION per list
                        fee(
                                b -> b.tokenAirdrop(TokenAirdropTransactionBody.newBuilder()
                                        .tokenTransfers(List.of(
                                                TokenTransferList.newBuilder()
                                                        .token(TokenID.newBuilder()
                                                                .tokenNum(1L)
                                                                .build())
                                                        .transfers(List.of(
                                                                AccountAmount.newBuilder()
                                                                        .accountID(
                                                                                AccountID.newBuilder()
                                                                                        .accountNum(2L)
                                                                                        .build())
                                                                        .amount(-100L)
                                                                        .build(),
                                                                AccountAmount.newBuilder()
                                                                        .accountID(
                                                                                AccountID.newBuilder()
                                                                                        .accountNum(3L)
                                                                                        .build())
                                                                        .amount(100L)
                                                                        .build()))
                                                        .build(),
                                                TokenTransferList.newBuilder()
                                                        .token(TokenID.newBuilder()
                                                                .tokenNum(2L)
                                                                .build())
                                                        .nftTransfers(List.of(NftTransfer.newBuilder()
                                                                .senderAccountID(
                                                                        AccountID.newBuilder()
                                                                                .accountNum(2L)
                                                                                .build())
                                                                .receiverAccountID(
                                                                        AccountID.newBuilder()
                                                                                .accountNum(3L)
                                                                                .build())
                                                                .serialNumber(1L)
                                                                .build()))
                                                        .build()))
                                        .build()),
                                2 * NODE_PORTION),
                        fee(
                                b -> b.tokenAssociate(TokenAssociateTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT),
                        fee(
                                b -> b.tokenBurn(TokenBurnTransactionBody.newBuilder()
                                        .amount(1L)
                                        .serialNumbers(List.of(1L))
                                        .build()),
                                HederaFunctionality.TOKEN_BURN),
                        fee(
                                b -> b.tokenCancelAirdrop(TokenCancelAirdropTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_CANCEL_AIRDROP),
                        fee(
                                b -> b.tokenClaimAirdrop(TokenClaimAirdropTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_CLAIM_AIRDROP),
                        fee(
                                b -> b.tokenCreation(TokenCreateTransactionBody.newBuilder()
                                        .adminKey(ed25519Key(1))
                                        .feeScheduleKey(ed25519Key(2))
                                        .freezeKey(ed25519Key(3))
                                        .kycKey(ed25519Key(4))
                                        .metadataKey(ed25519Key(5))
                                        .pauseKey(ed25519Key(6))
                                        .supplyKey(ed25519Key(7))
                                        .wipeKey(ed25519Key(8))
                                        .build()),
                                NODE_PORTION + serviceFee(HederaFunctionality.TOKEN_CREATE) + 7 * KEY_FEE),
                        fee(b -> b.tokenDeletion(TokenDeleteTransactionBody.DEFAULT), HederaFunctionality.TOKEN_DELETE),
                        fee(
                                b -> b.tokenDissociate(TokenDissociateTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT),
                        fee(
                                b -> b.tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE),
                        fee(
                                b -> b.tokenFreeze(TokenFreezeAccountTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_FREEZE_ACCOUNT),
                        fee(
                                b -> b.tokenGrantKyc(TokenGrantKycTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT),
                        // 2 NFT metadata entries; BASE is a one-time charge, TOKEN_MINT_NFT has includedCount=1
                        // so only 1 extra serial is charged beyond the first included one
                        fee(
                                b -> b.tokenMint(TokenMintTransactionBody.newBuilder()
                                        .metadata(List.of(Bytes.wrap(new byte[16]), Bytes.wrap(new byte[16])))
                                        .build()),
                                NODE_PORTION
                                        + serviceFee(HederaFunctionality.TOKEN_MINT)
                                        + TOKEN_MINT_NFT_BASE_FEE
                                        + TOKEN_MINT_NFT_FEE),
                        fee(b -> b.tokenPause(TokenPauseTransactionBody.DEFAULT), HederaFunctionality.TOKEN_PAUSE),
                        fee(b -> b.tokenReject(TokenRejectTransactionBody.DEFAULT), HederaFunctionality.TOKEN_REJECT),
                        fee(
                                b -> b.tokenRevokeKyc(TokenRevokeKycTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT),
                        fee(
                                b -> b.tokenUnfreeze(TokenUnfreezeAccountTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT),
                        fee(
                                b -> b.tokenUnpause(TokenUnpauseTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_UNPAUSE),
                        fee(
                                b -> b.tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                        .adminKey(ed25519Key(1))
                                        .feeScheduleKey(ed25519Key(2))
                                        .freezeKey(ed25519Key(3))
                                        .kycKey(ed25519Key(4))
                                        .metadataKey(ed25519Key(5))
                                        .pauseKey(ed25519Key(6))
                                        .supplyKey(ed25519Key(7))
                                        .wipeKey(ed25519Key(8))
                                        .build()),
                                NODE_PORTION + serviceFee(HederaFunctionality.TOKEN_UPDATE) + 7 * KEY_FEE),
                        fee(
                                b -> b.tokenUpdateNfts(TokenUpdateNftsTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_UPDATE_NFTS),
                        fee(
                                b -> b.tokenWipe(TokenWipeAccountTransactionBody.DEFAULT),
                                HederaFunctionality.TOKEN_ACCOUNT_WIPE),
                        // Miscellaneous
                        fee(b -> b.atomicBatch(AtomicBatchTransactionBody.DEFAULT), HederaFunctionality.ATOMIC_BATCH),
                        fee(b -> b.utilPrng(UtilPrngTransactionBody.DEFAULT), HederaFunctionality.UTIL_PRNG))
                .map(c -> Named.named(c.body().data().kind().protoName(), c));
    }

    private record FeeCase(TransactionBody body, long total) {}

    /** NODE_PORTION + schedule baseFee for {@code func}. */
    private static FeeCase fee(Consumer<TransactionBody.Builder> customizer, HederaFunctionality func) {
        return new FeeCase(body(customizer), NODE_PORTION + serviceFee(func));
    }

    /** Explicit total — for cases where the fee calculator deviates from the schedule's baseFee or adds extras. */
    private static FeeCase fee(Consumer<TransactionBody.Builder> customizer, long total) {
        return new FeeCase(body(customizer), total);
    }

    @Test
    void estimateFees() {
        // given
        var transaction = cryptoTransfer(0);

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getNodeBaseFeeTinycents()).isEqualTo(NODE_BASE_FEE);
        assertThat(result.getNetworkMultiplier()).isEqualTo(NETWORK_MULTIPLIER);
        assertThat(result.getServiceBaseFeeTinycents()).isZero(); // CryptoTransfer has no service fee
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION);
    }

    @Test
    void estimateFeesWithSignatures() {
        // when
        var base = service.estimateFees(cryptoTransfer(0), FeeEstimateMode.INTRINSIC);
        var withSignatures = service.estimateFees(cryptoTransfer(2), FeeEstimateMode.INTRINSIC);

        // then
        // 0 signatures: no extras charged → NODE_PORTION
        assertThat(base.totalTinycents()).isEqualTo(NODE_PORTION);
        // 2 signatures, SIGNATURES_INCLUDED=1 free, 1 extra at SIGNATURES_FEE in the node component:
        // nodeTotal = NODE_BASE_FEE + 1 × SIGNATURES_FEE
        // total    = nodeTotal × (1 + NETWORK_MULTIPLIER)
        long twoSigsNodeTotal = NODE_BASE_FEE + (2 - SIGNATURES_INCLUDED) * SIGNATURES_FEE;
        assertThat(withSignatures.totalTinycents()).isEqualTo(twoSigsNodeTotal * (1L + NETWORK_MULTIPLIER));
    }

    @Test
    void estimateFeesLegacyFormat() {
        // given
        var body = TransactionBody.newBuilder()
                .memo("legacy")
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        var transaction = Transaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .build();

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION);
    }

    @Test
    void loadsSimpleFeeScheduleFromDatabase() {
        // given
        var feeBytes = FeeSchedule.PROTOBUF.toBytes(FEE_SCHEDULE).toByteArray();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile()).fileData(feeBytes))
                .persist();

        // when
        var freshService = new FeeEstimationService(fileDataRepository, systemEntity);

        // then
        var result = freshService.estimateFees(cryptoTransfer(0), FeeEstimateMode.INTRINSIC);
        assertThat(result.getNodeBaseFeeTinycents()).isEqualTo(NODE_BASE_FEE);
        assertThat(result.getNetworkMultiplier()).isEqualTo(NETWORK_MULTIPLIER);
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION);
    }

    @Test
    void stateMode() {
        assertThatThrownBy(() -> service.estimateFees(cryptoTransfer(0), FeeEstimateMode.STATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("State-based fee estimation is not supported");
    }

    @Test
    void invalidTransaction() {
        // given
        var transaction = Transaction.newBuilder()
                .signedTransactionBytes(Bytes.wrap(domainBuilder.bytes(INVALID_TX_SIZE)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse transaction");
    }

    @Test
    void emptyTransaction() {
        assertThatThrownBy(() -> service.estimateFees(Transaction.DEFAULT, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction must contain body bytes or signed transaction bytes");
    }

    @Test
    void unknownTransactionType() {
        // given
        var body = TransactionBody.newBuilder().memo("test").build();
        var signedTransaction = SignedTransaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .build();
        var transaction = Transaction.newBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTransaction))
                .build();

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown transaction type");
    }

    @Test
    void contractCallIsGasOnly() {
        // ContractCall fees are paid entirely in gas; the fee calculator explicitly clears all fees.
        var body = TransactionBody.newBuilder()
                .contractCall(ContractCallTransactionBody.DEFAULT)
                .build();
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);
        assertThat(result.totalTinycents()).isZero();
    }

    @Test
    void cryptoDelete() {
        // given
        var body = TransactionBody.newBuilder()
                .cryptoDelete(CryptoDeleteTransactionBody.newBuilder()
                        .deleteAccountID(AccountID.newBuilder()
                                .accountNum(DELETE_ACCOUNT_NUM)
                                .build())
                        .transferAccountID(AccountID.newBuilder()
                                .accountNum(TRANSFER_ACCOUNT_NUM)
                                .build())
                        .build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getServiceBaseFeeTinycents()).isEqualTo(CRYPTO_DELETE_FEE);
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION + CRYPTO_DELETE_FEE);
    }

    @Test
    void cryptoCreateExtraKeys() {
        // given
        var key = Key.newBuilder()
                .keyList(KeyList.newBuilder()
                        .keys(List.of(ed25519Key(1), ed25519Key(2)))
                        .build())
                .build();
        var body = TransactionBody.newBuilder()
                .cryptoCreateAccount(
                        CryptoCreateTransactionBody.newBuilder().key(key).build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        // 2 keys in list: 1 key included, 1 extra key charged at KEY_FEE
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION + CRYPTO_CREATE_FEE + KEY_FEE);
    }

    @Test
    void consensusSubmitMessageShort() {
        // given
        var body = TransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .topicID(TopicID.newBuilder().topicNum(TOPIC_NUM).build())
                        .message(Bytes.wrap(new byte[SHORT_MESSAGE_BYTES]))
                        .build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getServiceBaseFeeTinycents()).isEqualTo(CONSENSUS_SUBMIT_MESSAGE_FEE);
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION + CONSENSUS_SUBMIT_MESSAGE_FEE);
    }

    @Test
    void consensusSubmitMessageLong() {
        // given
        var body = TransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .topicID(TopicID.newBuilder().topicNum(TOPIC_NUM).build())
                        .message(Bytes.wrap(new byte[LONG_MESSAGE_BYTES]))
                        .build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getServiceBaseFeeTinycents()).isEqualTo(CONSENSUS_SUBMIT_MESSAGE_FEE);
        assertThat(result.totalTinycents()).isEqualTo(LONG_MESSAGE_SUBMIT_TOTAL);
    }

    private static FeeSchedule loadFeeSchedule() {
        try (var in = V0490FileSchema.class.getResourceAsStream("/genesis/simpleFeesSchedules.json")) {
            if (in == null) throw new IllegalStateException("simpleFeesSchedules.json not found on classpath");
            return V0490FileSchema.parseSimpleFeesSchedules(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load simpleFeesSchedules.json", e);
        }
    }

    private static long extraFee(Extra extra) {
        return FEE_SCHEDULE.extras().stream()
                .filter(e -> e.name() == extra)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Extra fee not found: " + extra.protoName()))
                .fee();
    }

    private static long serviceFee(HederaFunctionality func) {
        return FEE_SCHEDULE.services().stream()
                .flatMap(s -> s.schedule().stream())
                .filter(f -> f.name() == func)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Service fee not found: " + func.protoName()))
                .baseFee();
    }

    private static TransactionBody body(Consumer<TransactionBody.Builder> customizer) {
        var builder = TransactionBody.newBuilder();
        customizer.accept(builder);
        return builder.build();
    }

    private static Transaction buildTransaction(TransactionBody body) {
        var signedTransaction = SignedTransaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .build();
        return Transaction.newBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTransaction))
                .build();
    }

    private static Key ed25519Key(int seed) {
        var key = new byte[ED25519_KEY_SIZE];
        key[0] = (byte) seed;
        return Key.newBuilder().ed25519(Bytes.wrap(key)).build();
    }

    private static CryptoApproveAllowanceTransactionBody cryptoApproveAllowanceBody() {
        var cryptoAllowance = CryptoAllowance.newBuilder()
                .amount(10L)
                .owner(AccountID.newBuilder().accountNum(1001L).build())
                .spender(AccountID.newBuilder().accountNum(1002L).build())
                .build();
        var nftWithSerials = NftAllowance.newBuilder()
                .owner(AccountID.newBuilder().accountNum(1001L).build())
                .serialNumbers(List.of(1L, 2L))
                .spender(AccountID.newBuilder().accountNum(1002L).build())
                .tokenId(TokenID.newBuilder().tokenNum(1L).build())
                .build();
        var nftApprovedForAllFalse = NftAllowance.newBuilder()
                .approvedForAll(false)
                .owner(AccountID.newBuilder().accountNum(1001L).build())
                .spender(AccountID.newBuilder().accountNum(1002L).build())
                .tokenId(TokenID.newBuilder().tokenNum(2L).build())
                .build();
        var nftApprovedForAllTrue = NftAllowance.newBuilder()
                .approvedForAll(true)
                .owner(AccountID.newBuilder().accountNum(1001L).build())
                .spender(AccountID.newBuilder().accountNum(1002L).build())
                .tokenId(TokenID.newBuilder().tokenNum(3L).build())
                .build();
        var nftWithSerialsAndApprovedForAll = NftAllowance.newBuilder()
                .approvedForAll(true)
                .owner(AccountID.newBuilder().accountNum(1001L).build())
                .serialNumbers(List.of(2L, 3L))
                .spender(AccountID.newBuilder().accountNum(1002L).build())
                .tokenId(TokenID.newBuilder().tokenNum(4L).build())
                .build();
        var tokenAllowance = TokenAllowance.newBuilder()
                .amount(10L)
                .owner(AccountID.newBuilder().accountNum(1001L).build())
                .spender(AccountID.newBuilder().accountNum(1002L).build())
                .tokenId(TokenID.newBuilder().tokenNum(1L).build())
                .build();
        return CryptoApproveAllowanceTransactionBody.newBuilder()
                .cryptoAllowances(List.of(cryptoAllowance, cryptoAllowance))
                .nftAllowances(List.of(
                        nftWithSerials,
                        nftApprovedForAllFalse,
                        nftApprovedForAllTrue,
                        nftWithSerialsAndApprovedForAll,
                        nftWithSerials,
                        nftApprovedForAllFalse))
                .tokenAllowances(List.of(tokenAllowance, tokenAllowance))
                .build();
    }

    private Transaction cryptoTransfer(int signatureCount) {
        var body = TransactionBody.newBuilder()
                .memo("test")
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        var sigPairs = new ArrayList<SignaturePair>(signatureCount);
        for (int i = 0; i < signatureCount; i++) {
            sigPairs.add(SignaturePair.newBuilder()
                    .pubKeyPrefix(Bytes.wrap(new byte[] {(byte) i}))
                    .ed25519(Bytes.wrap(new byte[ED25519_SIGNATURE_SIZE]))
                    .build());
        }
        var signedTransaction = SignedTransaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .sigMap(SignatureMap.newBuilder().sigPair(sigPairs).build())
                .build();
        return Transaction.newBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTransaction))
                .build();
    }
}
