// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CustomFeeRepositoryTest extends RestJavaIntegrationTest {

    private final CustomFeeRepository customFeeRepository;

    @Test
    void findById() {
        var expected = domainBuilder.customFee().persist();
        assertThat(customFeeRepository.findById(expected.getEntityId())).contains(expected);
    }
}
