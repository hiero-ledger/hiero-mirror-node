// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState.ValueState;

import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.hiero.mirror.web3.evm.store.UpdatableReferenceCache.UpdatableCacheUsageException;
import org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState.Entry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(SoftAssertionsExtension.class)
class UpdatableReferenceCacheTest {

    ///// Common types/values/methods for this test start here:

    static final String THIS_KEY = "THIS KEY";
    static final Long ORIGINAL_VALUE = 10L;
    static final Long UPDATED_VALUE = -25L;
    static final Long NEW_VALUE = -100L;
    final UpdatableReferenceCacheSpy sut = new UpdatableReferenceCacheSpy(); // cache that maps String->Long

    @InjectSoftAssertions
    SoftAssertions softly;

    void setInitialCacheLineState(@NonNull final ValueIs originalValueIs, @NonNull final ValueIs currentValueIs) {
        if (originalValueIs == ValueIs.NULL) {
            sut.addNullToOriginal(THIS_KEY);
        } else if (originalValueIs == ValueIs.NON_NULL) {
            sut.addToOriginal(THIS_KEY, ORIGINAL_VALUE);
        }

        if (currentValueIs == ValueIs.NULL) {
            sut.addNullToCurrent(THIS_KEY);
        } else if (currentValueIs == ValueIs.NON_NULL) {
            sut.addToCurrent(THIS_KEY, UPDATED_VALUE);
        }
    }

    ///// Per-test setup starts here:

    @Test
    void emptyCacheTest() {
        assertThat(sut.getCounts()).isEqualTo(new UpdatableReferenceCacheSpy.Counts(0, 0, 0));
    }

    @ParameterizedTest(name = "original {0} ✕ current {1} ⟶ (state {2}, with value from {3})")
    @CsvSource(
            useHeadersInDisplayName = true,
            textBlock =
                    """
        original, current, expected state,  expected value
        MISSING,  MISSING, NOT_YET_FETCHED, NOWHERE
        MISSING,  NULL,    INVALID,         NOWHERE
        MISSING,  NON_NULL,UPDATED,         CURRENT
        NULL,     MISSING, MISSING,         NOWHERE
        NULL,     NULL,    INVALID,         NOWHERE
        NULL,     NON_NULL,UPDATED,         CURRENT
        NON_NULL, MISSING, PRESENT,         ORIGINAL
        NON_NULL, NULL,    DELETED,         NOWHERE
        NON_NULL, NON_NULL,UPDATED,         CURRENT
    """)
    void getCurrentStateTestExhaustive(
            @NonNull final ValueIs originalValueIs,
            @NonNull final ValueIs currentValueIs,
            @NonNull final ValueState expectedState,
            @NonNull final ValueFrom expectedValueFrom) {

        setInitialCacheLineState(originalValueIs, currentValueIs);

        final var expected = new Entry(
                expectedState,
                switch (expectedValueFrom) {
                    case NOWHERE -> null;
                    case ORIGINAL -> ORIGINAL_VALUE;
                    case CURRENT -> UPDATED_VALUE;
                });

        final var actual = sut.getCacheLineState(THIS_KEY);
        softly.assertThat(actual).isEqualTo(expected);
        final var actualGet = sut.get(THIS_KEY);
        softly.assertThat(actualGet).isEqualTo(expected);
    }

    @Test
    void fillWhileNotYetFetchedIsOkTest() {
        sut.fill(THIS_KEY, ORIGINAL_VALUE);
        assertThat(sut.getOriginal()).containsOnlyKeys(THIS_KEY).containsEntry(THIS_KEY, ORIGINAL_VALUE);
        assertThat(sut.getCurrent()).isEmpty();
    }

    @ParameterizedTest(name = "state {0} (original {1} x current {2})")
    @CsvSource(
            useHeadersInDisplayName = true,
            textBlock =
                    """
        state,   original, current
        MISSING, NULL,     MISSING
        PRESENT, NON_NULL, MISSING
        UPDATED, NON_NULL, NON_NULL
        DELETED, NON_NULL, NULL
        INVALID, MISSING,  NULL
    """)
    void fillWhileInBadStateTest(
            @NonNull final ValueState state,
            @NonNull final ValueIs originalValueIs,
            @NonNull final ValueIs currentValueIs) {

        setInitialCacheLineState(originalValueIs, currentValueIs);

        assertThatExceptionOfType(
                        state == ValueState.INVALID ? IllegalStateException.class : UpdatableCacheUsageException.class)
                .isThrownBy(() -> sut.fill(THIS_KEY, NEW_VALUE));
    }

    @ParameterizedTest(name = "state {0} (original {1} x current {2})")
    @CsvSource(
            useHeadersInDisplayName = true,
            textBlock =
                    """
        state,           original, current
        NOT_YET_FETCHED, MISSING,  MISSING
        MISSING,         NULL,     MISSING
        PRESENT,         NON_NULL, MISSING
        UPDATED,         NULL,     NON_NULL
        UPDATED,         NON_NULL, NON_NULL
        DELETED,         NON_NULL, NULL
    """)
    void updateInAllowableScenariosTest(
            @NonNull final ValueState state,
            @NonNull final ValueIs originalValueIs,
            @NonNull final ValueIs currentValueIs) {

        setInitialCacheLineState(originalValueIs, currentValueIs);

        sut.update(THIS_KEY, NEW_VALUE);
        softly.assertThat(sut.get(THIS_KEY)).isEqualTo(new Entry(ValueState.UPDATED, NEW_VALUE));
        softly.assertThat(sut.getCurrent()).containsOnlyKeys(THIS_KEY).containsEntry(THIS_KEY, NEW_VALUE);
    }

    @Test
    void updateDisallowsCertainScenariosTest() {

        // invalid state is rejected
        sut.clearOriginal().clearCurrent().addNullToCurrent(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.update(THIS_KEY, 10L))
                .as("INVALID state")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid");

        // present with same value is rejected (that's not an update at all, it's probably a user error
        // where the value
        // - a reference type - was modified)
        sut.clearOriginal().clearCurrent().addToOriginal(THIS_KEY, ORIGINAL_VALUE);
        softly.assertThatThrownBy(() -> sut.update(THIS_KEY, ORIGINAL_VALUE))
                .as("PRESENT, overwriting with same value")
                .isInstanceOf(UpdatableCacheUsageException.class)
                .hasMessageContaining("Trying to update");
        softly.assertThat(sut.getOriginal())
                .as("PRESENT, overwriting with same value didn't change k/v")
                .containsOnlyKeys(THIS_KEY)
                .containsEntry(THIS_KEY, ORIGINAL_VALUE);
    }

    @ParameterizedTest(name = "state {0} (original {1} x current {2}) ⟶ result (state {3}, key exists? {4})")
    @CsvSource(
            useHeadersInDisplayName = true,
            textBlock =
                    """
        state,   original, current,  result,  key exists
        PRESENT, NON_NULL, MISSING,  DELETED, true
        UPDATED, NULL,     NON_NULL, MISSING, false
    """)
    void deleteInAllowableScenariosTest(
            final ValueState state,
            final ValueIs originalValueIs,
            final ValueIs currentValueIs,
            final ValueState endState,
            final boolean keyInCurrentAtEnd) {
        setInitialCacheLineState(originalValueIs, currentValueIs);

        sut.delete(THIS_KEY);
        softly.assertThat(sut.get(THIS_KEY)).isEqualTo(new Entry(endState, null));
        if (keyInCurrentAtEnd)
            softly.assertThat(sut.getCurrent()).containsOnlyKeys(THIS_KEY).containsEntry(THIS_KEY, null);
        else softly.assertThat(sut.getCurrent()).isEmpty();
    }

    @Test
    void deleteDisallowsCertainScenariosTest() {

        // invalid state is rejected
        sut.clearOriginal().clearCurrent().addNullToCurrent(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("INVALID state")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid");

        // not-yet-fetched state is rejected
        sut.clearOriginal().clearCurrent();
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("NOT_YET_FETCHED")
                .isInstanceOf(UpdatableCacheUsageException.class)
                .hasMessageContaining("fetched");

        // missing state is rejected
        sut.clearOriginal().clearCurrent().addNullToOriginal(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("MISSING")
                .isInstanceOf(UpdatableCacheUsageException.class)
                .hasMessageContaining("missing");

        // deleted state is rejected
        sut.clearOriginal()
                .clearCurrent()
                .addToOriginal(THIS_KEY, ORIGINAL_VALUE)
                .addNullToOriginal(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("DELETED")
                .isInstanceOf(UpdatableCacheUsageException.class)
                .hasMessageContaining("deleted");
    }

    @Test
    void coalesceFromTest() {

        final var src = new UpdatableReferenceCacheSpy(); // maps String->Long

        src.addToOriginal("SRC NOT NULL VALUE O", 10L)
                .addToOriginal("BOTH NOT NULL VALUE O", 20L)
                .addNullToOriginal("SRC NULL VALUE O")
                .addNullToOriginal("BOTH NULL VALUE O")
                .addToCurrent("SRC NOT NULL VALUE C", 30L)
                .addToCurrent("BOTH NOT NULL VALUE C", 40L)
                .addNullToCurrent("SRC NULL VALUE C")
                .addNullToCurrent("BOTH NULL VALUE C");

        sut.addToOriginal("SUT NOT NULL VALUE O", -10L)
                .addToOriginal("BOTH NOT NULL VALUE O", -20L)
                .addNullToOriginal("SUT NULL VALUE O")
                .addNullToOriginal("BOTH NULL VALUE O")
                .addToCurrent("SUT NOT NULL VALUE C", -30L)
                .addToCurrent("BOTH NOT NULL VALUE C", -40L)
                .addNullToCurrent("SUT NULL VALUE C")
                .addNullToCurrent("BOTH NULL VALUE C");

        final var expectedOriginal = new HashMap<>(sut.getOriginal());
        // spotless:off
        final var expectedCurrent = makeMapOf(
                String.class, Object.class,
                "SUT NOT NULL VALUE C", -30L,
                "BOTH NOT NULL VALUE C", 40L,
                "SUT NULL VALUE C",      null,
                "BOTH NULL VALUE C",     null,
                "SRC NOT NULL VALUE C",  30L,
                "SRC NULL VALUE C",      null);
        //spotless:on

        sut.coalesceFrom(src);

        softly.assertThat(sut.getOriginal())
                .as("original map is not touched by coalesce")
                .isEqualTo(expectedOriginal);

        softly.assertThat(sut.getCurrent())
                .as("current map is merged by coalesce")
                .isEqualTo(expectedCurrent);
    }

    /** Like `Map.of` but doesn't barf on `null` values */
    @NonNull
    <K, V> Map<K, V> makeMapOf(
            @NonNull final Class<K> klassK, @NonNull final Class<V> klassV, final Object... kvPairs) {
        if (kvPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must have even #arguments");
        }
        final var r = new HashMap<K, V>(kvPairs.length / 2);
        for (int i = 0; i < kvPairs.length; i += 2) {

            K key = verifyIsCorrectTypeOrNull(klassK, kvPairs[i], "Key argument at index %d".formatted(i));
            V value = verifyIsCorrectTypeOrNull(klassV, kvPairs[i + 1], "Value argument at index %d".formatted(i + 1));

            r.put(key, value);
        }
        return r;
    }

    /** Verify that some object is either null or of some specific type, returning that object cast appropriately (or
     * else throwing IllegalArgumentException). */
    @SuppressWarnings("unchecked") // OK because unchecked cast is immediately preceded by type check
    <T> T verifyIsCorrectTypeOrNull(
            @NonNull final Class<T> klass, @Nullable final Object obj, @NonNull final String customMessage) {
        return obj == null
                ? null
                : klass.isInstance(obj)
                        ? (T) obj
                        : throwException(
                                IllegalArgumentException.class,
                                "%s is not a %s (it's a %s)"
                                        .formatted(
                                                customMessage,
                                                klass.getTypeName(),
                                                obj.getClass().getTypeName()));
    }

    ///// Utility methods beyond this point:

    /** Throw an exception (with message) in the context of an _expression_ (where a `throw` statement by itself is
     * not acceptable).
     */
    <T> T throwException(@NonNull final Class<?> exceptionKlass, @NonNull final String message) {
        if (!(RuntimeException.class.isAssignableFrom(exceptionKlass))) {
            throw new IllegalArgumentException(
                    "%s is not a RuntimeException type".formatted(exceptionKlass.getTypeName()));
        }

        try {
            throw (RuntimeException)
                    exceptionKlass.getDeclaredConstructor(String.class).newInstance(message);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    enum ValueIs {
        MISSING,
        NULL,
        NON_NULL
    }

    enum ValueFrom {
        NOWHERE,
        ORIGINAL,
        CURRENT
    }
}
