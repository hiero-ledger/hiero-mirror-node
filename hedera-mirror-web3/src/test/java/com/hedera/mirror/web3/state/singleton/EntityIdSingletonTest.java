// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.mirror.web3.state.singleton.EntityIdSingleton.FIRST_USER_ENTITY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.repository.EntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdSingletonTest {

    private EntityIdSingleton entityIdSingleton;

    @Mock
    private EntityRepository entityRepository;

    @BeforeEach
    void setup() {
        entityIdSingleton = new EntityIdSingleton(entityRepository);
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsLessThanLastSystemAccount() {
        when(entityRepository.findMaxId()).thenReturn(900L);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccount() {
        long maxId = 2000;
        when(entityRepository.findMaxId()).thenReturn(maxId);
        assertEquals(maxId + 1, entityIdSingleton.get().number());
    }
}
