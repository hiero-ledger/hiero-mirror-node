// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.repository.HookRepository;

@Named
@RequiredArgsConstructor
final class HookServiceImpl implements HookService {

    private final EntityService entityService;
    private final HookRepository hookRepository;

    @Override
    public HookStorageResult getHookStorage(HookStorageRequest request) {
        var owner = entityService.lookup(request.getOwnerId());
        var hookId = new Hook.Id(request.getHookId(), owner.getId());
        if (!hookRepository.existsById(hookId)) {
            throw new RuntimeException("Hook not found");
        }
        var storage = hookRepository.findHookStorage(request, owner.getId());
        return new HookStorageResult(owner, storage);
    }

    @Override
    public List<Hook> getHooks(HooksRequest request) {
        var owner = entityService.lookup(request.getOwnerId());
        return hookRepository.findHooks(request, owner.getId());
    }
}
