// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableKVState;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;
import org.hiero.mirror.web3.state.singleton.DefaultSingleton;
import org.hiero.mirror.web3.state.singleton.SingletonState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateRegistryTest {
    private static final int MAX_KEYS_HINT = 123;
    private static final String READABLE_KV_SERVICE_NAME = "SERVICE_NAME";
    private static final String READABLE_KV_STATE_KEY = "KV_KEY";
    private static final String SINGLETON_KEY = "SINGLETON_KEY";

    private static Stream<Arguments> stateDefinition() {
        return Stream.of(
                Arguments.of(true, false, false, ReadableKVState.class, ReadableKVState.class.getSimpleName()),
                Arguments.of(false, true, false, DefaultSingleton.class, DefaultSingleton.class.getSimpleName()),
                Arguments.of(
                        false, false, true, ConcurrentLinkedDeque.class, ConcurrentLinkedDeque.class.getSimpleName()));
    }

    @Mock
    private Codec<String> mockCodec;

    private ReadableKVState<?, ?> kvState;
    private SingletonState<?> singleton;
    private StateRegistry registry;

    @BeforeEach
    void setUp() {
        kvState = mock(ReadableKVState.class);
        when(kvState.getStateKey()).thenReturn(READABLE_KV_STATE_KEY);

        singleton = mock(SingletonState.class);
        when(singleton.getKey()).thenReturn(SINGLETON_KEY);

        registry = new StateRegistry(List.of(kvState), List.of(singleton));
    }

    @Test
    @DisplayName("Lookup returns existing ReadableKVState")
    void returnsExistingKVState() {
        final var stateDefinition =
                new StateDefinition<>(READABLE_KV_STATE_KEY, mockCodec, mockCodec, MAX_KEYS_HINT, true, false, false);
        assertSame(kvState, registry.lookup(READABLE_KV_SERVICE_NAME, stateDefinition));
    }

    @Test
    @DisplayName("Lookup returns existing SingletonState")
    void returnsExistingSingleton() {
        final var stateDefinition =
                new StateDefinition<>(SINGLETON_KEY, mockCodec, mockCodec, MAX_KEYS_HINT, false, true, false);
        assertSame(singleton, registry.lookup(READABLE_KV_SERVICE_NAME, stateDefinition));
    }

    @ParameterizedTest(
            name = "Lookup returns new {4} when state key not present in state but present in default implementations")
    @MethodSource("stateDefinition")
    void returnsCorrectDefault(
            final boolean isOnDisk,
            final boolean isSingleton,
            final boolean isQueue,
            final Class<?> type,
            final String description) {
        final var stateKey = "UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=150]]";
        final var stateDefinition =
                new StateDefinition<>(stateKey, mockCodec, mockCodec, MAX_KEYS_HINT, isOnDisk, isSingleton, isQueue);
        final var state = registry.lookup(READABLE_KV_SERVICE_NAME, stateDefinition);
        assertThat(state).isNotNull().isInstanceOf(type);
    }

    @Test
    @DisplayName("Lookup throws exceptions when key is not present")
    void throwsExceptionWhenKeyNotPresent() {
        final var stateKey = "MISSING_KEY";
        final var stateDefinition =
                new StateDefinition<>(stateKey, mockCodec, mockCodec, MAX_KEYS_HINT, false, true, false);
        final var exception = assertThrows(
                UnsupportedOperationException.class, () -> registry.lookup(READABLE_KV_SERVICE_NAME, stateDefinition));
        assertThat(exception.getMessage()).isEqualTo("Unsupported state key: " + stateKey);
    }

    @ParameterizedTest(
            name =
                    "Lookup throws exception when empty state and stateKey is not in default implementations and parameters isOnDisk: {0}, isSingleton: {1}, isQueue: {2}")
    @MethodSource("stateDefinition")
    void throwsWhenEmptyStateAndNotInDefaultImpl(
            final boolean isOnDisk, final boolean isSingleton, final boolean isQueue) {
        registry = new StateRegistry(List.of(), List.of());
        final var stateDefinition = new StateDefinition<>(
                READABLE_KV_STATE_KEY, mockCodec, mockCodec, MAX_KEYS_HINT, isOnDisk, isSingleton, isQueue);
        final var exception = assertThrows(
                UnsupportedOperationException.class, () -> registry.lookup(READABLE_KV_SERVICE_NAME, stateDefinition));
        assertThat(exception.getMessage()).isEqualTo("Unsupported state key: " + READABLE_KV_STATE_KEY);
    }

    @ParameterizedTest(
            name =
                    "Lookup handles empty state when stateKey is default implementation and parameters isOnDisk: {0}, isSingleton: {1}, isQueue: {2} and class is: {4}")
    @MethodSource("stateDefinition")
    void handlesEmptyStates(
            final boolean isOnDisk,
            final boolean isSingleton,
            final boolean isQueue,
            final Class<?> type,
            final String description) {
        registry = new StateRegistry(List.of(), List.of());
        final var stateDefinition = new StateDefinition<>(
                V0490TokenSchema.STAKING_INFO_KEY, mockCodec, mockCodec, MAX_KEYS_HINT, isOnDisk, isSingleton, isQueue);
        final var result = registry.lookup(READABLE_KV_SERVICE_NAME, stateDefinition);
        assertThat(result).isInstanceOf(type);
    }
}
