// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.subscribe.rest.RestApiClient;
import com.hedera.mirror.monitor.validator.AccountIdValidator;
import com.hedera.mirror.rest.model.NetworkNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class ShardRealmProbeTest {

    private MonitorProperties monitorProperties;

    @Mock
    private RestApiClient restApiClient;

    private ShardRealmProbe shardRealmProbe;

    @BeforeEach
    void setup() {
        monitorProperties = new MonitorProperties();
        shardRealmProbe = new ShardRealmProbe(monitorProperties, restApiClient);
        TransactionSupplier.ACCOUNT_ID_VALIDATOR.set(null);
    }

    @AfterEach
    void tearDown() {
        TransactionSupplier.ACCOUNT_ID_VALIDATOR.set(null);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 0, 0.0.2
            1, 2, 1.2.2
            """)
    void defaultOperatorAccountId(long shard, long realm, String expectedOperatorAccountId) {
        // given
        doReturn(Flux.just(networkNode(0, shard, realm), networkNode(1, shard, realm)))
                .when(restApiClient)
                .getNodes();

        // when
        shardRealmProbe.init();

        // then
        assertThat(monitorProperties)
                .extracting(MonitorProperties::getOperator)
                .returns(expectedOperatorAccountId, OperatorProperties::getAccountId);
        assertThat(TransactionSupplier.ACCOUNT_ID_VALIDATOR.get())
                .returns(shard, AccountIdValidator::shard)
                .returns(realm, AccountIdValidator::realm);
    }

    @Test
    void nonDefaultOperatorAccountId() {
        // given
        monitorProperties.getOperator().setAccountId("0.0.1200");
        doReturn(Flux.just(networkNode(0, 0, 2), networkNode(1, 0, 2)))
                .when(restApiClient)
                .getNodes();

        // when, then
        assertThatThrownBy(() -> shardRealmProbe.init()).isInstanceOf(IllegalArgumentException.class);
        assertThat(TransactionSupplier.ACCOUNT_ID_VALIDATOR.get()).isNull();
    }

    @Test
    void getNodesEmpty() {
        doReturn(Flux.empty()).when(restApiClient).getNodes();
        assertThatThrownBy(() -> shardRealmProbe.init()).isInstanceOf(IllegalStateException.class);
        assertThat(TransactionSupplier.ACCOUNT_ID_VALIDATOR.get()).isNull();
    }

    @Test
    void getNodesThrows() {
        doThrow(RuntimeException.class).when(restApiClient).getNodes();
        assertThatThrownBy(() -> shardRealmProbe.init()).isInstanceOf(RuntimeException.class);
        assertThat(TransactionSupplier.ACCOUNT_ID_VALIDATOR.get()).isNull();
    }

    private NetworkNode networkNode(long nodeId, long shard, long realm) {
        String nodeAccountId = "%d.%d.%d".formatted(shard, realm, nodeId + 3);
        return new NetworkNode().nodeAccountId(nodeAccountId).nodeId(nodeId);
    }
}
