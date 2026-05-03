// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

/** Internal row for mapping hook storage (current or historical) to the REST model. */
public record HookStorageSlot(long timestampNanos, byte[] key, byte[] value) {}
