// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.rest.model.TimestampRange;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NftAllowanceMapperTest {

    private CommonMapper commonMapper;
    private DomainBuilder domainBuilder;
    private NftAllowanceMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new NftAllowanceMapperImpl(commonMapper);
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        var allowance = domainBuilder.nftAllowance().get();
        var to = commonMapper.mapTimestamp(allowance.getTimestampLower());

        assertThat(mapper.map(List.of(allowance)))
                .first()
                .returns(EntityId.of(allowance.getOwner()).toString(), NftAllowance::getOwner)
                .returns(EntityId.of(allowance.getTokenId()).toString(), NftAllowance::getTokenId)
                .returns(EntityId.of(allowance.getSpender()).toString(), NftAllowance::getSpender)
                .returns(allowance.isApprovedForAll(), NftAllowance::getApprovedForAll)
                .satisfies(a -> assertThat(a.getTimestamp())
                        .returns(to, TimestampRange::getFrom)
                        .returns(null, TimestampRange::getTo));
    }

    @Test
    void mapNulls() {
        var allowance = new com.hedera.mirror.common.domain.entity.NftAllowance();

        assertThat(mapper.map(allowance))
                .returns(null, NftAllowance::getOwner)
                .returns(null, NftAllowance::getTokenId)
                .returns(null, NftAllowance::getSpender)
                .returns(false, NftAllowance::getApprovedForAll)
                .returns(null, NftAllowance::getTimestamp);
    }
}
