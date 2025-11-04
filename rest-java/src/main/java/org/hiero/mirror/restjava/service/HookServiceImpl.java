// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class HookServiceImpl implements HookService {

    private static final String HOOK_ID = "hookId";

    private final HookRepository hookRepository;
    private final EntityService entityService;

    @Override
    public Collection<Hook> getHooks(HooksRequest request) {
        final var sort = Sort.by(request.getOrder(), HOOK_ID);
        final var page = PageRequest.of(0, request.getLimit(), sort);
        final var id = entityService.lookup(request.getOwnerId());
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();

        if (request.getHookIds().isEmpty()) {
            return hookRepository.findByOwnerIdAndHookIdBetween(id.getId(), lowerBound, upperBound, page);
        } else {
            // Both equal and range filters are present.
            final var idsInRange = request.getHookIds().stream()
                    .filter(hookId -> hookId >= lowerBound && hookId <= upperBound)
                    .toList();

            if (idsInRange.isEmpty()) {
                return Collections.emptyList();
            }

            return hookRepository.findByOwnerIdAndHookIdIn(id.getId(), idsInRange, page);
        }
    }
}
