/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.rest.model.ConsensusCustomFees;
import java.util.Collections;
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
