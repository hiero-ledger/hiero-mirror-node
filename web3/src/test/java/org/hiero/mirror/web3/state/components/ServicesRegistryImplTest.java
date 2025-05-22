// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.swirlds.state.lifecycle.Service;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ServicesRegistryImplTest {

    @Mock
    private StateRegistry stateRegistry;

    private ServicesRegistryImpl servicesRegistry;

    @BeforeEach
    void setUp() {
        servicesRegistry = new ServicesRegistryImpl(stateRegistry);
    }

    @Test
    void testEmptyRegistrations() {
        assertThat(servicesRegistry.registrations()).isEmpty();
    }

    @Test
    void testRegister() {
        Service service = new FileServiceImpl();
        servicesRegistry.register(service);
        assertThat(servicesRegistry.registrations()).hasSize(1);
    }

    @Test
    void testEmptySubRegistry() {
        Service service = new FileServiceImpl();
        Service service2 = new EntityIdService();
        ServicesRegistryImpl subRegistry = (ServicesRegistryImpl)
                servicesRegistry.subRegistryFor(service.getServiceName(), service2.getServiceName());
        assertThat(subRegistry.registrations()).isEmpty();
    }

    @Test
    void testSubRegistry() {
        Service service = new FileServiceImpl();
        Service service2 = new EntityIdService();
        servicesRegistry.register(service);
        servicesRegistry.register(service2);
        ServicesRegistryImpl subRegistry = (ServicesRegistryImpl)
                servicesRegistry.subRegistryFor(service.getServiceName(), service2.getServiceName());
        assertThat(subRegistry.registrations()).hasSize(2);
    }
}
