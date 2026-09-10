// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryTimeoutConfigurationTest {

    private final Web3Properties web3Properties = new Web3Properties();

    @Mock
    private Connection connection;

    @Mock
    private DataSource dataSource;

    @Mock
    private ObjectProvider<Web3Properties> web3PropertiesProvider;

    @Test
    void nonDataSourceBeanIsLeftUntouched() {
        var bean = new Object();
        assertThat(postProcessor().postProcessAfterInitialization(bean, "bean")).isSameAs(bean);
    }

    @Test
    void noTimeoutOutsideContractCallContext() throws SQLException {
        web3Properties.setRequestTimeout(Duration.ZERO);
        wrap().getConnection().prepareStatement("select 1");
        verify(connection).prepareStatement("select 1");
    }

    @Test
    void noTimeoutBeforeDeadline() throws SQLException {
        var wrapped = wrap();
        ContractCallContext.run(context -> {
            prepareStatement(wrapped);
            return null;
        });
        verify(connection).prepareStatement("select 1");
    }

    @Test
    void timesOutPastDeadline() {
        web3Properties.setRequestTimeout(Duration.ZERO);
        var wrapped = wrap();
        ContractCallContext.run(context -> {
            assertThatThrownBy(() -> prepareStatement(wrapped))
                    .isInstanceOf(QueryTimeoutException.class)
                    .hasMessageContaining("Transaction timed out after");
            return null;
        });
    }

    private BeanPostProcessor postProcessor() {
        when(web3PropertiesProvider.getObject()).thenReturn(web3Properties);
        return QueryTimeoutConfiguration.queryTimeoutDataSourcePostProcessor(web3PropertiesProvider);
    }

    @SneakyThrows
    private DataSource wrap() {
        when(dataSource.getConnection()).thenReturn(connection);
        return (DataSource) postProcessor().postProcessAfterInitialization(dataSource, "dataSource");
    }

    @SneakyThrows
    private void prepareStatement(DataSource wrapped) {
        wrapped.getConnection().prepareStatement("select 1");
    }
}
