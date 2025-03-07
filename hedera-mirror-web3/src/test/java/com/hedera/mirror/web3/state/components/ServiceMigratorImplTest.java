// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistry.Registration;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.VersionConfig;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceMigratorImplTest {

    @Mock
    private Metrics metrics;

    @Mock
    private MirrorNodeState mirrorNodeState;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private SchemaRegistryImpl schemaRegistry;

    @Mock
    private SchemaRegistry mockSchemaRegistry;

    @Mock
    private ServicesRegistryImpl servicesRegistry;

    @Mock
    private ServicesRegistry mockServicesRegistry;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private State mockState;

    private VersionedConfiguration bootstrapConfig;

    private ServiceMigratorImpl serviceMigrator;

    @BeforeEach
    void initialize() {
        serviceMigrator = new ServiceMigratorImpl();
        bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
    }

    @Test
    void testCreationVersionOfWithMirrorNodeState() {
        assertDoesNotThrow(() -> serviceMigrator.creationVersionOf(mirrorNodeState));
    }

    @Test
    void testCreationVersionOfWithInvalidState() {
        assertThrows(IllegalArgumentException.class, () -> serviceMigrator.creationVersionOf(mockState));
    }

    @Test
    void doMigrations() {
        final var mockServiceRegistration = mock(Registration.class);
        when(servicesRegistry.registrations()).thenReturn(Set.of(mockServiceRegistration));
        when(mockServiceRegistration.registry()).thenReturn(schemaRegistry);

        assertDoesNotThrow(() -> serviceMigrator.doMigrations(
                mirrorNodeState,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                new ConfigProviderImpl().getConfiguration(),
                networkInfo,
                metrics,
                startupNetworks));
    }

    @Test
    void doMigrationsWithMultipleRegistrations() {
        Service service1 = mock(Service.class);
        Service service2 = mock(Service.class);
        when(service2.getServiceName()).thenReturn("testService2");

        SchemaRegistry registry1 = mock(SchemaRegistryImpl.class);
        SchemaRegistry registry2 = mock(SchemaRegistryImpl.class);

        Registration registration1 = new Registration(service1, registry1);
        Registration registration2 = new Registration(service2, registry2);
        when(servicesRegistry.registrations()).thenReturn(Set.of(registration1, registration2));
        assertDoesNotThrow(() -> serviceMigrator.doMigrations(
                mirrorNodeState,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                new ConfigProviderImpl().getConfiguration(),
                networkInfo,
                metrics,
                startupNetworks));
    }

    @Test
    void doMigrationsWithMultipleRegistrationsWithInvalidSchemaRegistry() {
        Service service1 = mock(Service.class);
        Service service2 = mock(Service.class);

        SchemaRegistry registry1 = mock(SchemaRegistryImpl.class);

        // Invalid schema registry type
        SchemaRegistry registry2 = mock(SchemaRegistry.class);

        Registration registration1 = new Registration(service1, registry1);
        Registration registration2 = new Registration(service2, registry2);
        when(servicesRegistry.registrations()).thenReturn(Set.of(registration1, registration2));

        var servicesVersion = bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion();
        var servicesSoftwareVersion = new ServicesSoftwareVersion(servicesVersion);
        var configuration = new ConfigProviderImpl().getConfiguration();

        var exception = assertThrows(IllegalArgumentException.class, () -> {
            serviceMigrator.doMigrations(
                    mirrorNodeState,
                    servicesRegistry,
                    null,
                    servicesSoftwareVersion,
                    configuration,
                    configuration,
                    networkInfo,
                    metrics,
                    startupNetworks);
        });

        assertThat(exception.getMessage()).isEqualTo("Can only be used with SchemaRegistryImpl instances");
    }

    @Test
    void doMigrationsInvalidState() {
        var servicesVersion = bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion();
        var servicesSoftwareVersion = new ServicesSoftwareVersion(servicesVersion);
        var configuration = new ConfigProviderImpl().getConfiguration();

        var exception = assertThrows(IllegalArgumentException.class, () -> {
            serviceMigrator.doMigrations(
                    mockState,
                    servicesRegistry,
                    null,
                    servicesSoftwareVersion,
                    configuration,
                    configuration,
                    networkInfo,
                    metrics,
                    startupNetworks);
        });

        assertThat(exception.getMessage()).isEqualTo("Can only be used with MirrorNodeState instances");
    }

    @Test
    void doMigrationsInvalidServicesRegistry() {
        var servicesVersion = bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion();
        var servicesSoftwareVersion = new ServicesSoftwareVersion(servicesVersion);
        var configuration = new ConfigProviderImpl().getConfiguration();

        var exception = assertThrows(IllegalArgumentException.class, () -> {
            serviceMigrator.doMigrations(
                    mirrorNodeState,
                    mockServicesRegistry,
                    null,
                    servicesSoftwareVersion,
                    configuration,
                    configuration,
                    networkInfo,
                    metrics,
                    startupNetworks);
        });

        assertThat(exception.getMessage()).isEqualTo("Can only be used with ServicesRegistryImpl instances");
    }

    @Test
    void doMigrationsInvalidSchemaRegistry() {
        final var mockServiceRegistration = mock(Registration.class);
        when(servicesRegistry.registrations()).thenReturn(Set.of(mockServiceRegistration));
        when(mockServiceRegistration.registry()).thenReturn(mockSchemaRegistry);

        var servicesVersion = bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion();
        var servicesSoftwareVersion = new ServicesSoftwareVersion(servicesVersion);
        var configuration = new ConfigProviderImpl().getConfiguration();

        var exception = assertThrows(IllegalArgumentException.class, () -> {
            serviceMigrator.doMigrations(
                    mirrorNodeState,
                    servicesRegistry,
                    null,
                    servicesSoftwareVersion,
                    configuration,
                    configuration,
                    networkInfo,
                    metrics,
                    startupNetworks);
        });

        assertThat(exception.getMessage()).isEqualTo("Can only be used with SchemaRegistryImpl instances");
    }
}
