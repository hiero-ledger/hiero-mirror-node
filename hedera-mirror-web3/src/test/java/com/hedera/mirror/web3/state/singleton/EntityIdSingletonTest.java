// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.FIRST_USER_ENTITY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.EntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class EntityIdSingletonTest {

    private EntityIdSingleton entityIdSingleton;

    @Mock
    private EntityRepository entityRepository;

    @BeforeEach
    void setup() {
        entityIdSingleton = new EntityIdSingleton(entityRepository, new MirrorNodeEvmProperties());
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsLessThanLastSystemAccount() {
        when(entityRepository.findMaxId()).thenReturn(900L);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsNull() {
        when(entityRepository.findMaxId()).thenReturn(0L);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccount() {
        long maxId = 2000;
        when(entityRepository.findMaxId()).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + 1);
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccountWithIncrement() {
        long maxId = 2000;
        when(entityRepository.findMaxId()).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + 1);
    }
}
