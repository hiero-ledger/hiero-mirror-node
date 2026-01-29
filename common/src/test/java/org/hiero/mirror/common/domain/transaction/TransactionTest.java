// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class TransactionTest {

    private static final String EXPECTED_JSON_TEMPLATE = """
                    {
                      "batch_key": null,
                      "consensus_timestamp": 1684791152000000000,
                      "charged_tx_fee": 1,
                      "entity_id": 2,
                      "errata": "INSERT",
                      "high_volume": true,
                      "index":4,
                      "inner_transactions": null,
                      "initial_balance": 5,
                      "itemized_transfer": %s,
                      "max_custom_fees": %s,
                      "memo": "BgcI",
                      "max_fee": 9,
                      "nft_transfer": %s,
                      "node_account_id": 3,
                      "nonce": 19,
                      "parent_consensus_timestamp": 20,
                      "payer_account_id": 21,
                      "result": 22,
                      "scheduled": false,
                      "transaction_bytes": "FxgZ",
                      "transaction_hash": "Ghsc",
                      "transaction_record_bytes": "HR4f",
                      "type": 29,
                      "valid_duration_seconds": 30,
                      "valid_start_ns": 31
                    }
                    """;
    private static final String EXPECTED_MAX_CUSTOM_FEES = "[\"AQI=\", \"Cgs=\"]";
    private static final String EXPECTED_ITEMIZED_TRANSFER_VALUE = """
                    "[{\\"amount\\":-200,\\"entity_id\\":50,\\"is_approval\\":true},{\\"amount\\":200,\\"entity_id\\":51,\\"is_approval\\":false}]"
                    """;
    private static final String EXPECTED_NFT_TRANSFER_VALUE = """
                    "[{\\"is_approval\\":false,\\"receiver_account_id\\":10,\\"sender_account_id\\":11,\\"serial_number\\":12,\\"token_id\\":13},{\\"is_approval\\":true,\\"receiver_account_id\\":14,\\"sender_account_id\\":15,\\"serial_number\\":16,\\"token_id\\":17}]"
                    """;

    @Test
    void highVolume() {
        var transaction = Transaction.builder().build();
        assertThat(transaction.getHighVolume()).isNull();

        transaction.setHighVolume(true);
        assertThat(transaction.getHighVolume()).isTrue();

        transaction.setHighVolume(false);
        assertThat(transaction.getHighVolume()).isFalse();
    }

    @Test
    void addItemizedTransfer() {
        var transaction = Transaction.builder().build();
        assertThat(transaction.getItemizedTransfer()).isNull();

        var domainBuilder = new DomainBuilder();
        var itemizedTransfer1 = ItemizedTransfer.builder()
                .amount(domainBuilder.number())
                .entityId(domainBuilder.entityId())
                .isApproval(false)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer1);
        assertThat(transaction.getItemizedTransfer()).containsExactly(itemizedTransfer1);

        var itemizedTransfer2 = ItemizedTransfer.builder()
                .amount(domainBuilder.number())
                .entityId(domainBuilder.entityId())
                .isApproval(true)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer2);
        assertThat(transaction.getItemizedTransfer()).containsExactly(itemizedTransfer1, itemizedTransfer2);
    }

    @Test
    void addNftTransfer() {
        var transaction = Transaction.builder().build();
        assertThat(transaction.getNftTransfer()).isNull();

        var domainBuilder = new DomainBuilder();
        var nftTransfer1 = domainBuilder.nftTransfer().get();
        transaction.addNftTransfer(nftTransfer1);
        assertThat(transaction.getNftTransfer()).containsExactly(nftTransfer1);

        var nftTransfer2 = domainBuilder.nftTransfer().get();
        transaction.addNftTransfer(nftTransfer2);
        assertThat(transaction.getNftTransfer()).containsExactly(nftTransfer1, nftTransfer2);
    }

    @Test
    void addInnerTransaction() {
        var domainBuilder = new DomainBuilder();

        var batchTransaction = domainBuilder
                .transaction()
                .customize(builder -> builder.type(TransactionType.ATOMIC_BATCH.getProtoId()))
                .get();
        assertThat(batchTransaction.getInnerTransactions()).isNull();

        var innerTransaction = domainBuilder.transaction().get();
        batchTransaction.addInnerTransaction(innerTransaction);

        var innerTransaction2 = domainBuilder.transaction().get();
        batchTransaction.addInnerTransaction(innerTransaction2);

        var expectedInnerTransactions = List.of(
                innerTransaction.getPayerAccountId().getId(),
                innerTransaction.getValidStartNs(),
                innerTransaction2.getPayerAccountId().getId(),
                innerTransaction2.getValidStartNs());

        assertThat(batchTransaction.getInnerTransactions()).isEqualTo(expectedInnerTransactions);

        var nonBatchTransaction = domainBuilder.transaction().get();
        assertThatThrownBy(() -> nonBatchTransaction.addInnerTransaction(innerTransaction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Inner transactions can only be added to atomic batch transaction");
    }

    @Test
    void toJson() throws Exception {
        // given
        var transaction = getTransaction();
        var itemizedTransfer1 = ItemizedTransfer.builder()
                .amount(-200L)
                .entityId(EntityId.of(50))
                .isApproval(true)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer1);

        var itemizedTransfer2 = ItemizedTransfer.builder()
                .amount(200L)
                .entityId(EntityId.of(51))
                .isApproval(false)
                .build();
        transaction.addItemizedTransfer(itemizedTransfer2);

        var nftTransfer1 = new NftTransfer();
        nftTransfer1.setIsApproval(false);
        nftTransfer1.setReceiverAccountId(EntityId.of(10L));
        nftTransfer1.setSenderAccountId(EntityId.of(11L));
        nftTransfer1.setSerialNumber(12L);
        nftTransfer1.setTokenId(EntityId.of(13L));
        transaction.addNftTransfer(nftTransfer1);

        NftTransfer nftTransfer2 = new NftTransfer();
        nftTransfer2.setIsApproval(true);
        nftTransfer2.setReceiverAccountId(EntityId.of(14L));
        nftTransfer2.setSenderAccountId(EntityId.of(15L));
        nftTransfer2.setSerialNumber(16L);
        nftTransfer2.setTokenId(EntityId.of(17L));
        transaction.addNftTransfer(nftTransfer2);

        // when
        String actual = OBJECT_MAPPER.writeValueAsString(transaction);

        // then
        String expected = String.format(
                EXPECTED_JSON_TEMPLATE,
                EXPECTED_ITEMIZED_TRANSFER_VALUE,
                EXPECTED_MAX_CUSTOM_FEES,
                EXPECTED_NFT_TRANSFER_VALUE);
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    @Test
    void toJsonNullItemizedTransferAndNullMaxCustomFeesAndNullNftTransfer() throws Exception {
        // given
        var transaction = getTransaction();
        transaction.setMaxCustomFees(null);

        // when
        String actual = OBJECT_MAPPER.writeValueAsString(transaction);

        // then
        String expected = String.format(EXPECTED_JSON_TEMPLATE, "null", "null", "null");
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    }

    private Transaction getTransaction() {
        var transaction = new Transaction();
        transaction.setConsensusTimestamp(1684791152000000000L);
        transaction.setChargedTxFee(1L);
        transaction.setEntityId(EntityId.of(2L));
        transaction.setErrata(ErrataType.INSERT);
        transaction.setHighVolume(true);
        transaction.setIndex(4);
        transaction.setInitialBalance(5L);
        transaction.setMaxCustomFees(new byte[][] {{0x1, 0x2}, {0xa, 0xb}});
        transaction.setMemo(new byte[] {6, 7, 8});
        transaction.setMaxFee(9L);
        transaction.setNodeAccountId(EntityId.of(3L));
        transaction.setNonce(19);
        transaction.setParentConsensusTimestamp(20L);
        transaction.setPayerAccountId(EntityId.of(21L));
        transaction.setResult(22);
        transaction.setScheduled(false);
        transaction.setTransactionBytes(new byte[] {23, 24, 25});
        transaction.setTransactionHash(new byte[] {26, 27, 28});
        transaction.setTransactionRecordBytes(new byte[] {29, 30, 31});
        transaction.setType(29);
        transaction.setValidDurationSeconds(30L);
        transaction.setValidStartNs(31L);
        return transaction;
    }
}
