// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.token.AbstractNft;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftHistory;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Named
public class NftBuilder extends AbstractEntityBuilder<AbstractNft, AbstractNft.AbstractNftBuilder<?, ?>> {
    private static final Map<String, BiFunction<Object, SpecBuilderContext, Object>> PARAMETER_CONVERTERS =
            Map.of("metadata", RAW_BYTES_CONVERTER);

    public NftBuilder() {
        super(PARAMETER_CONVERTERS);
    }

    @Override
    protected AbstractNft.AbstractNftBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        var builder = builderContext.isHistory() ? NftHistory.builder() : Nft.builder();
        return builder.createdTimestamp(0L).deleted(false).metadata(new byte[0]).serialNumber(0L);
    }

    @Override
    protected AbstractNft getFinalEntity(
            AbstractNft.AbstractNftBuilder<?, ?> builder, Map<String, Object> entityAttributes) {
        var nft = builder.build();
        if (nft.getTimestampRange() == null) {
            nft.setTimestampRange(Range.atLeast(nft.getCreatedTimestamp()));
        }
        return nft;
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::nfts;
    }
}
