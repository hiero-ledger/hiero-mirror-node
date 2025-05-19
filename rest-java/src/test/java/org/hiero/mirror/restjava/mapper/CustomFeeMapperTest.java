// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.rest.model.ConsensusCustomFees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomFeeMapperTest {

    private CommonMapper commonMapper;
    private DomainBuilder domainBuilder;
    private FixedCustomFeeMapper fixedCustomFeeMapper;
    private CustomFeeMapper mapper;

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
        commonMapper = new CommonMapperImpl();
        fixedCustomFeeMapper = new FixedCustomFeeMapperImpl(commonMapper);
        mapper = new CustomFeeMapperImpl(fixedCustomFeeMapper, commonMapper);
    }

    @Test
    void map() {
        var customFee = domainBuilder.customFee().get();
        var expected = new ConsensusCustomFees()
                .createdTimestamp(commonMapper.mapLowerRange(customFee.getTimestampRange()))
                .fixedFees(fixedCustomFeeMapper.map(customFee.getFixedFees()));
        assertThat(mapper.map(customFee)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mapEmptyOrNullFixedFees(boolean nullFixedFees) {
        var customFee = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(nullFixedFees ? null : Collections.emptyList()))
                .get();
        var expected = new ConsensusCustomFees()
                .createdTimestamp(commonMapper.mapLowerRange(customFee.getTimestampRange()))
                .fixedFees(Collections.emptyList());
        assertThat(mapper.map(customFee)).isEqualTo(expected);
    }
}
