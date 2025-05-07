// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Named
public class TransactionBuilder extends AbstractEntityBuilder<Transaction, Transaction.TransactionBuilder> {
    private static final Map<String, String> ATTRIBUTE_MAP = Map.of("valid_start_timestamp", "validStartNs");

    public TransactionBuilder() {
        super(Map.of(), ATTRIBUTE_MAP);
    }

    @Override
    protected Transaction.TransactionBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return Transaction.builder()
                .chargedTxFee(NODE_FEE + NETWORK_FEE + SERVICE_FEE)
                .maxCustomFees(new byte[0][0])
                .maxFee(33L)
                .nonce(0)
                .result(22)
                .transactionBytes(Base64.getEncoder().encode("bytes".getBytes()))
                .transactionHash(DEFAULT_TRANSACTION_HASH)
                .type(TransactionType.CRYPTOTRANSFER.getProtoId())
                .validDurationSeconds(11L)
                .index(1);
    }

    @Override
    protected Transaction getFinalEntity(Transaction.TransactionBuilder builder, Map<String, Object> entityAttributes) {
        var transaction = builder.build();

        if (transaction.getValidStartNs() == null) {
            transaction.setValidStartNs(transaction.getConsensusTimestamp() - 1);
        }

        return transaction;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> Optional.ofNullable(specSetup.transactions())
                .map(transactions -> transactions.stream()
                        .map(transaction -> {
                            var nftTransfer = (List<Map<String, Object>>) transaction.get("nft_transfer");

                            if (nftTransfer != null) {
                                var nftTransfers = nftTransfer.stream()
                                        .map(transfer -> {
                                            var builder = NftTransfer.builder();
                                            customizeWithSpec(builder, transfer, new SpecBuilderContext(transfer));
                                            return builder.build();
                                        })
                                        .toList();
                                transaction = new HashMap<>(transaction);
                                transaction.put("nft_transfer", nftTransfers);
                            }
                            return transaction;
                        })
                        .toList())
                .orElse(null);
    }
}
