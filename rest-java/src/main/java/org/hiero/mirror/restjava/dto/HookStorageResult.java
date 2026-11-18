// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;

public record HookStorageResult(EntityId ownerId, List<HookStorageSlot> storage) {}
