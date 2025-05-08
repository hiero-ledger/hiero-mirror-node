// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.collect.Streams;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Named
public class CryptoTransferBuilder extends AbstractEntityBuilder<CryptoTransfer, CryptoTransfer.CryptoTransferBuilder> {
    private static final Map<String, String> ATTRIBUTE_MAP = Map.of("account", "entityId");

    public CryptoTransferBuilder() {
        super(Map.of(), ATTRIBUTE_MAP);
    }

    @Override
    protected CryptoTransfer.CryptoTransferBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return CryptoTransfer.builder();
    }

    @Override
    protected CryptoTransfer getFinalEntity(
            CryptoTransfer.CryptoTransferBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> Streams.concat(
                        Optional.ofNullable(specSetup.cryptoTransfers()).orElse(Collections.emptyList()).stream(),
                        Optional.ofNullable(specSetup.transactions()).orElse(Collections.emptyList()).stream())
                .map(this::extractTransfers)
                .flatMap(List::stream)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTransfers(Map<String, Object> transaction) {
        var transfers = (List<Map<String, Object>>) transaction.get("transfers");
        var timestamp = transaction.get("consensus_timestamp");
        var nodeAccountId = transaction.getOrDefault("nodeAccountId", DEFAULT_NODE_ID.getId());
        var payerAccountId = transaction.getOrDefault("payer_account_id", transaction.get("payerAccountId"));
        var amount = Long.parseLong(transaction.getOrDefault("amount", NODE_FEE).toString());
        var senderAccountId = transaction.getOrDefault("senderAccountId", payerAccountId);
        var recipientAccountId = transaction.getOrDefault("recipientAccountId", nodeAccountId);
        var treasuryAccountId = transaction.getOrDefault("treasuryAccountId", DEFAULT_FEE_COLLECTOR_ID.getId());

        if (transfers != null && !transfers.isEmpty()) {
            return transfers.stream()
                    .peek(transfer -> {
                        transfer.putIfAbsent("payer_account_id", payerAccountId);
                        transfer.putIfAbsent("consensus_timestamp", timestamp);
                    })
                    .toList();

        } else if (payerAccountId != null) {
            var chargedTxFee = Long.parseLong(transaction
                    .getOrDefault("charged_tx_fee", NODE_FEE + NETWORK_FEE + SERVICE_FEE)
                    .toString());
            if (chargedTxFee > 0) {
                var recipientTransfer = Map.of(
                        "consensus_timestamp", timestamp,
                        "amount", amount,
                        "entity_id", recipientAccountId,
                        "payer_account_id", payerAccountId,
                        "is_approval", false);

                var feeTransfer = Map.of(
                        "consensus_timestamp", timestamp,
                        "amount", NETWORK_FEE,
                        "entity_id", treasuryAccountId,
                        "payer_account_id", payerAccountId,
                        "is_approval", false);

                var senderTransfer = Map.of(
                        "consensus_timestamp", timestamp,
                        "amount", -amount - NETWORK_FEE,
                        "entity_id", senderAccountId,
                        "payer_account_id", payerAccountId,
                        "is_approval", false);

                return List.of(recipientTransfer, feeTransfer, senderTransfer);
            }
        }

        return Collections.emptyList();
    }
}
