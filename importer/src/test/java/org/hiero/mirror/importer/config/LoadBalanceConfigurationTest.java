// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hiero.mirror.importer.db.DBProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoadBalanceConfigurationTest {

    private static final String SQL = "select 1";

    private final DBProperties dbProperties = new DBProperties();

    @Mock
    private Connection connection;

    @Mock
    private DataSource dataSource;

    @Mock
    private ObjectProvider<DBProperties> dbPropertiesProvider;

    @Test
    void nonDataSourceBeanIsLeftUntouched() {
        var bean = new Object();
        assertThat(postProcessor().postProcessAfterInitialization(bean, "bean")).isSameAs(bean);
    }

    @Test
    void otherDataSourceBeanIsLeftUntouched() {
        assertThat(postProcessor().postProcessAfterInitialization(dataSource, "flywayDataSource"))
                .isSameAs(dataSource);
    }

    @Test
    void noPrefixWhenLoadBalanceEnabled() throws SQLException {
        wrap().getConnection().prepareStatement(SQL);
        verify(connection).prepareStatement(SQL);
    }

    @Test
    void prefixWhenLoadBalanceDisabled() throws SQLException {
        dbProperties.setLoadBalance(false);
        wrap().getConnection().prepareStatement(SQL);
        verify(connection).prepareStatement("/* NO PGPOOL LOAD BALANCE */\n" + SQL);
    }

    private BeanPostProcessor postProcessor() {
        when(dbPropertiesProvider.getObject()).thenReturn(dbProperties);
        return LoadBalanceConfiguration.noLoadBalanceDataSourcePostProcessor(dbPropertiesProvider);
    }

    private DataSource wrap() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        return (DataSource) postProcessor().postProcessAfterInitialization(dataSource, "dataSource");
    }
}
