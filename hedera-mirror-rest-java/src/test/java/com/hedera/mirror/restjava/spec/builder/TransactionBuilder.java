// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.collect.Streams;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFeeLimit;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.TokenID;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Named
public class TransactionBuilder extends AbstractEntityBuilder<Transaction, Transaction.TransactionBuilder> {
    private static final Map<String, BiFunction<Object, SpecBuilderContext, Object>> METHOD_PARAMETER_CONVERTERS =
            Map.of("transactionHash", HEX_OR_BASE64_CONVERTER);
    private static final Map<String, String> ATTRIBUTE_MAP =
            Map.of("valid_start_timestamp", "validStartNs", "accountNum", "setAccountNum");

    public TransactionBuilder() {
        super(METHOD_PARAMETER_CONVERTERS, ATTRIBUTE_MAP);
    }

    @Override
    protected Transaction.TransactionBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return Transaction.builder()
                .chargedTxFee(NODE_FEE + NETWORK_FEE + SERVICE_FEE)
                .maxCustomFees(new byte[0][0])
                .maxFee(33L)
                .nonce(0)
                .result(22)
                .transactionBytes("bytes".getBytes())
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
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> Streams.concat(
                        Optional.ofNullable(specSetup.transactions()).orElse(Collections.emptyList()).stream(),
                        Optional.ofNullable(specSetup.cryptoTransfers()).orElse(Collections.emptyList()).stream())
                .map(this::addNftTransfers)
                .map(this::addMaxCustomFees)
                .toList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> addNftTransfers(Map<String, Object> transaction) {
        var copy = new HashMap<>(transaction);
        var nftTransfer = (List<Map<String, Object>>) transaction.get("nft_transfer");

        if (nftTransfer != null) {
            var nftTransfers = nftTransfer.stream()
                    .map(transfer -> {
                        var builder = NftTransfer.builder();
                        customizeWithSpec(builder, transfer, new SpecBuilderContext(transfer));
                        return builder.build();
                    })
                    .toList();
            copy.put("nft_transfer", nftTransfers);
        }

        return copy;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> addMaxCustomFees(Map<String, Object> transaction) {
        var copy = new HashMap<>(transaction);
        var maxCustomFees = (List<Map<String, Object>>) transaction.get("max_custom_fees");
        if (maxCustomFees != null) {
            var customFees = maxCustomFees.stream()
                    .map(customFee -> {
                        var accountId = (Map<String, Object>) customFee.get("accountId");
                        var accountNum = Long.parseLong(
                                accountId.getOrDefault("accountNum", 0).toString());
                        var shardNum = Long.parseLong(accountId
                                .getOrDefault("shardNum", COMMON_PROPS.getShard())
                                .toString());
                        var accountRealmNum = Long.parseLong(accountId
                                .getOrDefault("realmNum", COMMON_PROPS.getRealm())
                                .toString());

                        var fees = Optional.ofNullable((List<Map<String, Object>>) customFee.get("fees"))
                                .orElse(new ArrayList<>())
                                .stream()
                                .map(fee -> {
                                    var tokenId = (Map<String, Object>) fee.get("denominatingTokenId");

                                    var feeBuilder = FixedFee.newBuilder()
                                            .setAmount(Long.parseLong(fee.getOrDefault("amount", 0)
                                                    .toString()));
                                    if (tokenId != null) {
                                        feeBuilder.setDenominatingTokenId(TokenID.newBuilder()
                                                .setShardNum(shardNum)
                                                .setRealmNum(accountRealmNum)
                                                .setTokenNum(Long.parseLong(tokenId.getOrDefault("tokenNum", 0)
                                                        .toString())));
                                    }
                                    return feeBuilder.build();
                                })
                                .toList();

                        var builder = CustomFeeLimit.newBuilder()
                                .setAccountId(AccountID.newBuilder()
                                        .setShardNum(shardNum)
                                        .setRealmNum(accountRealmNum)
                                        .setAccountNum(accountNum))
                                .addAllFees(fees);

                        return builder.build().toByteArray();
                    })
                    .toArray(byte[][]::new);
            copy.put("max_custom_fees", customFees);
        }
        return copy;
    }
}
