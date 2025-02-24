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
