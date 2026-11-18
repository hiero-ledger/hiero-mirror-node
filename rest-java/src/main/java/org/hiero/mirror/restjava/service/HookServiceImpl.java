// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageResult;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.repository.HookRepository;

@Named
@RequiredArgsConstructor
final class HookServiceImpl implements HookService {

    private final EntityService entityService;
    private final HookRepository hookRepository;

    @Override
    public HookStorageResult getHookStorage(HookStorageRequest request) {
        final var ownerId = entityService.lookup(request.getOwnerId());
        final var storage = hookRepository.findHookStorage(request, ownerId.getId());
        return new HookStorageResult(ownerId, storage);
    }

    @Override
    public Collection<Hook> getHooks(HooksRequest request) {
        final var ownerId = entityService.lookup(request.getOwnerId());
        return hookRepository.findHooks(request, ownerId.getId());
    }
}
