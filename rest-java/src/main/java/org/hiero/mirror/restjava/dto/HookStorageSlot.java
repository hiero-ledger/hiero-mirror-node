// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.jspecify.annotations.Nullable;

/**
 * A hook storage slot read from either {@code hook_storage} (current state) or {@code hook_storage_change}
 * (historical state). The value is null for a historical read-only change since no value was written.
 */
public record HookStorageSlot(byte[] key, long timestamp, byte @Nullable [] value) {}
