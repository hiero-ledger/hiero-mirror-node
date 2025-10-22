// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema.NODE_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema.NODE_REWARDS_STATE_ID;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_KEY;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.token.NodeRewards;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableStates;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;
import org.hiero.mirror.web3.utils.TestSchemaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
class SchemaRegistryImplIntegrationTest extends Web3IntegrationTest {

    private static final String SERVICE_NAME = "testService";

    private static Stream<Arguments> stateDefinition() {
        return Stream.of(
                Arguments.of(
                        Set.of(
                                StateDefinition.singleton(
                                        CONGESTION_LEVEL_STARTS_STATE_ID,
                                        CONGESTION_LEVEL_STARTS_KEY,
                                        CongestionLevelStarts.PROTOBUF),
                                StateDefinition.onDisk(
                                        TOKENS_STATE_ID, TOKENS_KEY, TokenID.PROTOBUF, Token.PROTOBUF, 1)),
                        SERVICE_NAME,
                        "states")
                //                Arguments.of(
                //                        Set.of(
                //                                StateDefinition.queue(
                //                                        "UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=112]]",
                //                                        ProtoBytes.PROTOBUF),
                //                                StateDefinition.onDisk(SCHEDULES_BY_EXPIRY_SEC_STATE_ID,
                //                                        SCHEDULES_BY_EXPIRY_SEC_KEY, ProtoLong.PROTOBUF,
                // ScheduleList.PROTOBUF, 1)),
                //                        "otherService",
                //                        "default implementations")
                );
    }

    private final SemanticVersion previousVersion = new SemanticVersion(0, 46, 0, "", "");
    private final MirrorNodeState mirrorNodeState;
    private final StartupNetworks startupNetworks;
    private final StateRegistry stateRegistry;

    private SchemaRegistryImpl schemaRegistry;
    private String serviceName = "TEST";

    private Configuration config;

    @BeforeEach
    void setup() {
        schemaRegistry = new SchemaRegistryImpl(serviceName, new SchemaApplications(), stateRegistry);
        config = new ConfigProviderImpl().getConfiguration();
    }

    @ParameterizedTest(name = "Migrate adds state when state keys are present in {2}")
    @MethodSource("stateDefinition")
    void migrateWithStateKeysPresent(
            final Set<StateDefinition<?, ?>> stateDef, final String serviceName, final String description) {
        final var expectedKeys =
                stateDef.stream().map(StateDefinition::stateKey).collect(Collectors.toSet());

        final var schema =
                new TestSchemaBuilder(previousVersion).withStates(stateDef).build();
        schemaRegistry.register(schema);
        migrate(serviceName);
        final var readableStates = mirrorNodeState.getReadableStates(serviceName);
        final var writableStates = mirrorNodeState.getWritableStates(serviceName);
        validateState(stateDef, readableStates, expectedKeys);
        validateState(stateDef, writableStates, expectedKeys);
    }

    @Test
    @DisplayName("Migrate removes state keys listed in statesToRemove")
    void migrateRemovesObsoleteStates() {
        final var stateId = NODE_REWARDS_STATE_ID;
        final var stateKey = NODE_REWARDS_KEY;
        final var stateDefSet = Set.of(StateDefinition.singleton(stateId, stateKey, NodeRewards.PROTOBUF));
        final var schema = new TestSchemaBuilder(previousVersion)
                .withStates(stateDefSet)
                .withStatesToRemove(Set.of(stateId))
                .build();
        schemaRegistry.register(schema);
        migrate(SERVICE_NAME);

        final var readableStates = mirrorNodeState.getReadableStates(SERVICE_NAME);
        final var writableStates = mirrorNodeState.getWritableStates(SERVICE_NAME);
        assertThat(readableStates.stateIds()).doesNotContain(stateId);
        assertThat(writableStates.stateIds()).doesNotContain(stateId);
    }

    @Test
    @DisplayName("Migrate is skipped when no schemas are present")
    void migrateSkipWhenNoSchemasPresent() {
        final var emptyService = "emptyService";
        migrate(emptyService);
        final var readableStates = mirrorNodeState.getReadableStates(emptyService);
        final var writableStates = mirrorNodeState.getWritableStates(emptyService);
        assertThat(readableStates.stateIds()).isEmpty();
        assertThat(writableStates.stateIds()).isEmpty();
    }

    private void migrate(final String serviceName) {
        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, config, config, new HashMap<>(), startupNetworks);
    }

    private void validateState(
            final Set<StateDefinition<?, ?>> stateDef, final ReadableStates states, final Set<String> expectedKeys) {
        assertThat(states).isNotNull();
        assertThat(states.stateIds().stream().map(Object::toString).toList())
                .hasSize(stateDef.size())
                .containsExactlyInAnyOrderElementsOf(expectedKeys);
    }
}
