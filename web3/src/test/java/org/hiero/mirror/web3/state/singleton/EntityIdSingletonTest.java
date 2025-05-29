// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.FIRST_USER_ENTITY_ID;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    // Method that provides the test data
    private static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }

    @BeforeEach
    void setup() {
        var systemEntity = new SystemEntity(commonProperties);
        entityIdSingleton = new EntityIdSingleton(
                entityRepository, new MirrorNodeEvmProperties(commonProperties, systemEntity), commonProperties);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void shouldReturnFirstUserEntityIdWhenMaxIdIsLessThanLastSystemAccount(long shard, long realm) {
        long maxId = 900L;
        long expectedId = Math.max(FIRST_USER_ENTITY_ID, maxId + DEFAULT_ENTITY_NUM_BUFFER + 1);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findMaxId(shard, realm)).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(expectedId);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void shouldReturnFirstUserEntityIdWhenMaxIdIsNull(long shard, long realm) {
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findMaxId(shard, realm)).thenReturn(null);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccount(long shard, long realm) {
        long maxId = 2000;
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findMaxId(shard, realm)).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + DEFAULT_ENTITY_NUM_BUFFER + 1);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void shouldIncrementIdMultipleTimesWithCache(long shard, long realm) {
        long currentMaxId = 1001L;
        long expected = currentMaxId + 1 + DEFAULT_ENTITY_NUM_BUFFER;
        long end = 1005L;
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findMaxId(shard, realm)).thenReturn(currentMaxId);
        for (long expectedId = currentMaxId + 1; expectedId <= end; expectedId++) {
            assertThat(entityIdSingleton.get().number()).isEqualTo(expected);
            currentMaxId++;
        }
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccountWithIncrement(long shard, long realm) {
        long maxId = 2000;
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findMaxId(shard, realm)).thenReturn(maxId);
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + DEFAULT_ENTITY_NUM_BUFFER + 1);
    }
}
