// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
    void findByInvalidShard() {
        var entityId = EntityId.of(1, 0, 200);
        assertThatThrownBy(() -> service.findById(entityId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID " + entityId + " has an invalid shard. Shard must be 0");
    }

    @Test
    void findByIdNotFound() {
        var entityId = EntityId.of(10L);
        assertThatThrownBy(() -> service.findById(entityId)).isInstanceOf(EntityNotFoundException.class);
    }
}
