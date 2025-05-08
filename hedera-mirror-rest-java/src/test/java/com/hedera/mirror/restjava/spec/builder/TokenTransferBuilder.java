// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Named
public class TokenTransferBuilder extends AbstractEntityBuilder<TokenTransfer, TokenTransfer.TokenTransferBuilder> {
    private static final Map<String, String> ATTRIBUTE_MAP = Map.of("account", "accountId");

    public TokenTransferBuilder() {
        super(Map.of(), ATTRIBUTE_MAP);
    }

    @Override
    protected TokenTransfer.TokenTransferBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return TokenTransfer.builder();
    }

    @Override
    protected TokenTransfer getFinalEntity(
            TokenTransfer.TokenTransferBuilder builder, Map<String, Object> entityAttributes) {

        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> Stream.concat(
                        Optional.ofNullable(specSetup.cryptoTransfers()).orElse(Collections.emptyList()).stream(),
                        Optional.ofNullable(specSetup.transactions()).orElse(Collections.emptyList()).stream())
                .map(this::toTokenTransfers)
                .flatMap(List::stream)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toTokenTransfers(Map<String, Object> cryptoTransfer) {
        return ((List<Map<String, Object>>) cryptoTransfer.getOrDefault("token_transfer_list", new ArrayList<>()))
                .stream()
                        .map(tokenTransfer -> {
                            var timestamp = cryptoTransfer.get("consensus_timestamp");
                            var tokenId = tokenTransfer.get("token_id");
                            tokenTransfer.putIfAbsent("consensus_timestamp", cryptoTransfer.get("consensus_timestamp"));
                            tokenTransfer.putIfAbsent(
                                    "payer_account_id",
                                    cryptoTransfer.getOrDefault(
                                            "payer_account_id", cryptoTransfer.get("payerAccountId")));

                            Map<String, Object> updated = new HashMap<>(tokenTransfer);
                            var builder = TokenTransfer.Id.builder();
                            customizeWithSpec(builder, tokenTransfer, new SpecBuilderContext(tokenTransfer));

                            updated.put("id", builder.build());
                            return updated;
                        })
                        .toList();
    }
}
