// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.FIRST_USER_ENTITY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.CommonProperties;
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

    private CommonProperties commonProperties;

    @Mock
    private EntityRepository entityRepository;

    @BeforeEach
    void setup() {
        commonProperties = new CommonProperties();
        entityIdSingleton = new EntityIdSingleton(entityRepository, new MirrorNodeEvmProperties(), commonProperties);
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsLessThanLastSystemAccount() {
        when(entityRepository.findMaxId(0, 0)).thenReturn(900L);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsNull() {
        when(entityRepository.findMaxId(0, 0)).thenReturn(null);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccount() {
        long maxId = 2000;
        when(entityRepository.findMaxId(0, 0)).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + 1);
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccountNonZeroRealmShard() {
        long maxId = 2000;
        commonProperties.setShard(1);
        commonProperties.setRealm(1);
        when(entityRepository.findMaxId(1, 1)).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + 1);
    }

    @Test
    void shouldIncrementIdMultipleTimes() {
        long currentMaxId = 1001L;
        long end = 1005L;
        for (long expectedId = currentMaxId + 1; expectedId <= end; expectedId++) {
            when(entityRepository.findMaxId(0, 0)).thenReturn(currentMaxId);
            assertThat(entityIdSingleton.get().number()).isEqualTo(expectedId);
            currentMaxId++;
        }
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccountWithIncrement() {
        long maxId = 2000;
        when(entityRepository.findMaxId(0, 0)).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + 1);
    }
}
