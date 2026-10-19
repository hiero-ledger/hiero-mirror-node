// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.hiero.mirror.restjava.dto.HooksRequest;

interface HookRepositoryCustom {

    List<HookStorageSlot> findHookStorage(HookStorageRequest request, long ownerId);

    List<Hook> findHooks(HooksRequest request, long ownerId);
}
