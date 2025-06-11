// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.FIRST_USER_ENTITY_ID;
import static org.mockito.Mockito.when;

import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, ContextExtension.class})
class EntityIdSingletonTest {

    private static final long DEFAULT_ENTITY_NUM_BUFFER = 1000L;

    private EntityIdSingleton entityIdSingleton;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private SystemEntity systemEntity;

    @BeforeEach
    void setup() {
        entityIdSingleton =
                new EntityIdSingleton(entityRepository, new MirrorNodeEvmProperties(commonProperties, systemEntity));
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsLessThanLastSystemAccount() {
        long maxId = 900L;
        long expectedId = Math.max(FIRST_USER_ENTITY_ID, maxId + DEFAULT_ENTITY_NUM_BUFFER + 1);
        when(entityRepository.findMaxId()).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(expectedId);
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsNull() {
        when(entityRepository.findMaxId()).thenReturn(null);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccount() {
        long maxId = 2000;
        when(entityRepository.findMaxId()).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + DEFAULT_ENTITY_NUM_BUFFER + 1);
    }

    @Test
    void shouldIncrementIdMultipleTimesWithCache() {
        long currentMaxId = 1001L;
        long expected = currentMaxId + 1 + DEFAULT_ENTITY_NUM_BUFFER;
        long end = 1005L;
        when(entityRepository.findMaxId()).thenReturn(currentMaxId);
        for (long expectedId = currentMaxId + 1; expectedId <= end; expectedId++) {
            assertThat(entityIdSingleton.get().number()).isEqualTo(expected);
            currentMaxId++;
        }
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccountWithIncrement() {
        long maxId = 2000;
        when(entityRepository.findMaxId()).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + DEFAULT_ENTITY_NUM_BUFFER + 1);
    }
}
