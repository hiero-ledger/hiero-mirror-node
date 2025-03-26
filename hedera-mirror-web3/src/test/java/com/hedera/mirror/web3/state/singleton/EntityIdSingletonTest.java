// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.FIRST_USER_ENTITY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.EntityRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class EntityIdSingletonTest {

    private EntityIdSingleton entityIdSingleton;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private EntityRepository entityRepository;

    @BeforeEach
    void setup() {
        entityIdSingleton = new EntityIdSingleton(
                entityRepository, new MirrorNodeEvmProperties(commonProperties), commonProperties);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void shouldReturnFirstUserEntityIdWhenMaxIdIsLessThanLastSystemAccount(long shard, long realm) {
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findMaxId(shard, realm)).thenReturn(900L);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
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
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + 1);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void shouldIncrementIdMultipleTimes(long shard, long realm) {
        long currentMaxId = 1001L;
        long end = 1005L;
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        for (long expectedId = currentMaxId + 1; expectedId <= end; expectedId++) {
            when(entityRepository.findMaxId(shard, realm)).thenReturn(currentMaxId);
            assertThat(entityIdSingleton.get().number()).isEqualTo(expectedId);
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
        assertThat(entityIdSingleton.get().number()).isEqualTo(maxId + 1);
    }

    // Method that provides the test data
    private static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }
}
