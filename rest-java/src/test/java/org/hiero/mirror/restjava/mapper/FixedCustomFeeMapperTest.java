// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.rest.model.FixedCustomFee;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FixedCustomFeeMapperTest {

    private CommonMapper commonMapper;
    private DomainBuilder domainBuilder;
    private FixedCustomFeeMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        domainBuilder = new DomainBuilder();
        mapper = new FixedCustomFeeMapperImpl(commonMapper);
    }

    @Test
    void map() {
        var fixedFees = List.of(domainBuilder.fixedFee(), domainBuilder.fixedFee());
        var expected = fixedFees.stream()
                .map(fixedFee -> new FixedCustomFee()
                        .amount(fixedFee.getAmount())
                        .collectorAccountId(commonMapper.mapEntityId(fixedFee.getCollectorAccountId()))
                        .denominatingTokenId(commonMapper.mapEntityId(fixedFee.getDenominatingTokenId())))
                .toList();
        assertThat(mapper.map(fixedFees)).containsExactlyElementsOf(expected);
    }

    @Test
    void mapEmptyOrNull() {
        assertThat(mapper.map(List.of())).isEmpty();
        assertThat(mapper.map((Collection<FixedFee>) null)).isEmpty();
    }
}
