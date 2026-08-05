// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.dto.HookStorageSlot;

public record HookStorageResult(EntityId ownerId, List<HookStorageSlot> storage) {}
