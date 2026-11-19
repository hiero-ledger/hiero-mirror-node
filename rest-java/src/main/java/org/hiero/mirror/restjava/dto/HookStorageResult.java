// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.Collection;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.HookStorage;

public record HookStorageResult(EntityId ownerId, Collection<HookStorage> storage) {}
