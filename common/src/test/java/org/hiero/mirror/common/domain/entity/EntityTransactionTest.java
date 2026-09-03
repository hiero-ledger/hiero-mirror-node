// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EntityTransactionTest {

    @Test
    void setTypeAndResultClampsToSmallint() {
        var entityTransaction = new EntityTransaction();
        entityTransaction.setType(Short.MAX_VALUE + 1);
        entityTransaction.setResult(Integer.MAX_VALUE);
        assertThat(entityTransaction.getType()).isEqualTo((int) Short.MAX_VALUE);
        assertThat(entityTransaction.getResult()).isEqualTo((int) Short.MAX_VALUE);

        entityTransaction.setType(Short.MIN_VALUE - 1);
        entityTransaction.setResult(Integer.MIN_VALUE);
        assertThat(entityTransaction.getType()).isEqualTo((int) Short.MIN_VALUE);
        assertThat(entityTransaction.getResult()).isEqualTo((int) Short.MIN_VALUE);

        var built = EntityTransaction.builder()
                .type(Short.MAX_VALUE + 1)
                .result(Integer.MAX_VALUE)
                .build();
        assertThat(built.getType()).isEqualTo((int) Short.MAX_VALUE);
        assertThat(built.getResult()).isEqualTo((int) Short.MAX_VALUE);
    }
}
