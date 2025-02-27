// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.mirror.web3.repository.EntityRepositoryTest.FIRST_USER_ENTITY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.HederaConfig;
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

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private VersionedConfiguration versionedConfiguration;

    @Mock
    private HederaConfig hederaConfig;

    @BeforeEach
    void setup() {
        entityIdSingleton = new EntityIdSingleton(entityRepository, mirrorNodeEvmProperties);
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsLessThanLastSystemAccount() {
        when(mirrorNodeEvmProperties.getVersionedConfiguration()).thenReturn(versionedConfiguration);
        when(versionedConfiguration.getConfigData(HederaConfig.class)).thenReturn(hederaConfig);
        when(hederaConfig.firstUserEntity()).thenReturn(FIRST_USER_ENTITY_ID);
        when(entityRepository.findMaxId(FIRST_USER_ENTITY_ID)).thenReturn(900L);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnFirstUserEntityIdWhenMaxIdIsNull() {
        when(mirrorNodeEvmProperties.getVersionedConfiguration()).thenReturn(versionedConfiguration);
        when(versionedConfiguration.getConfigData(HederaConfig.class)).thenReturn(hederaConfig);
        when(hederaConfig.firstUserEntity()).thenReturn(FIRST_USER_ENTITY_ID);
        when(entityRepository.findMaxId(FIRST_USER_ENTITY_ID)).thenReturn(null);
        assertThat(entityIdSingleton.get().number()).isEqualTo(FIRST_USER_ENTITY_ID);
    }

    @Test
    void shouldReturnNextIdWhenMaxIdIsGreaterThanLastSystemAccount() {
        when(mirrorNodeEvmProperties.getVersionedConfiguration()).thenReturn(versionedConfiguration);
        when(versionedConfiguration.getConfigData(HederaConfig.class)).thenReturn(hederaConfig);
        when(hederaConfig.firstUserEntity()).thenReturn(FIRST_USER_ENTITY_ID);
        long maxId = 2000;
        when(entityRepository.findMaxId(FIRST_USER_ENTITY_ID)).thenReturn(maxId);
        assertEquals(maxId + 1, entityIdSingleton.get().number());
    }
}
