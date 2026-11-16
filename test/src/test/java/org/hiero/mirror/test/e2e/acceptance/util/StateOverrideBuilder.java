// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.util;

import java.util.ArrayList;
import org.hiero.mirror.rest.model.StateOverride;
import org.hiero.mirror.rest.model.StorageEntry;

/** Builds one address override, encoding storage entries as 32-byte words. */
public final class StateOverrideBuilder {

    private final StateOverride override;

    private StateOverrideBuilder(StateOverride override) {
        this.override = override;
    }

    public static StateOverrideBuilder forAddress(String address) {
        return new StateOverrideBuilder(
                new StateOverride().address(address).state(new ArrayList<>()).stateDiff(new ArrayList<>()));
    }

    public StateOverrideBuilder balance(long tinybars) {
        override.balance(requireNonNegative(tinybars));
        return this;
    }

    public StateOverrideBuilder nonce(long nonce) {
        override.nonce(requireNonNegative(nonce));
        return this;
    }

    public StateOverrideBuilder code(String runtimeBytecode) {
        override.code(runtimeBytecode);
        return this;
    }

    public StateOverrideBuilder state(long slot, long value) {
        if (!override.getStateDiff().isEmpty()) {
            throw new IllegalStateException("state and state_diff are mutually exclusive");
        }
        override.addStateItem(storageEntry(slot, value));
        return this;
    }

    public StateOverrideBuilder stateDiff(long slot, long value) {
        if (!override.getState().isEmpty()) {
            throw new IllegalStateException("state and state_diff are mutually exclusive");
        }
        override.addStateDiffItem(storageEntry(slot, value));
        return this;
    }

    public StateOverride build() {
        return from(override).override;
    }

    private static StateOverrideBuilder from(StateOverride source) {
        return new StateOverrideBuilder(new StateOverride()
                .address(source.getAddress())
                .balance(source.getBalance())
                .code(source.getCode())
                .nonce(source.getNonce())
                .state(source.getState() == null ? new ArrayList<>() : new ArrayList<>(source.getState()))
                .stateDiff(source.getStateDiff() == null ? new ArrayList<>() : new ArrayList<>(source.getStateDiff())));
    }

    private static long requireNonNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Override values must be nonnegative");
        }
        return value;
    }

    private static StorageEntry storageEntry(long slot, long value) {
        return new StorageEntry().key("0x%064x".formatted(slot)).value("0x%064x".formatted(value));
    }
}
