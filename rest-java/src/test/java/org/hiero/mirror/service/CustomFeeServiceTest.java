// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.RestJavaIntegrationTest;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.service.CustomFeeService;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CustomFeeServiceTest extends RestJavaIntegrationTest {

    private final CustomFeeService service;

    @Test
    void findById() {
        var customFee = domainBuilder.customFee().persist();
        assertThat(service.findById(EntityId.of(customFee.getEntityId()))).isEqualTo(customFee);
    }

    @Test
    void findByIdNotFound() {
        var entityId = EntityId.of(10L);
        assertThatThrownBy(() -> service.findById(entityId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Custom fee for entity not found");
    }
}
