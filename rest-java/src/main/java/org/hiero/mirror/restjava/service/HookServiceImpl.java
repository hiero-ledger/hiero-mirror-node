// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@NullMarked
@RequiredArgsConstructor
final class HookServiceImpl implements HookService {

    private final HookRepository hookRepository;
    private final EntityService entityService;

    @Override
    public Collection<Hook> getHooks(HooksRequest request) {
        final var sort = Sort.by(request.getOrder(), "hookId");
        final var page = PageRequest.of(0, request.getLimit(), sort);

        final var id = entityService.lookup(request.getOwnerId());

        final boolean hasRangeFilters =
                (request.getHookIdLowerBoundInclusive() != null || request.getHookIdUpperBoundInclusive() != null);
        final boolean hasEqFilters = (request.getHookIdEqualsFilters() != null
                && !request.getHookIdEqualsFilters().isEmpty());

        if (!hasRangeFilters && !hasEqFilters) {
            return hookRepository.findByOwnerId(id.getId(), page);

        } else if (!hasRangeFilters && hasEqFilters) {
            return hookRepository.findByOwnerIdAndHookIdIn(id.getId(), request.getHookIdEqualsFilters(), page);

        } else if (hasRangeFilters && !hasEqFilters) {
            return hookRepository.findByOwnerIdAndHookIdBetween(
                    id.getId(), request.getHookIdLowerBoundInclusive(), request.getHookIdUpperBoundInclusive(), page);

        } else {
            // Both 'eq' and 'range' filters are present.
            final long lowerBound = request.getHookIdLowerBoundInclusive();
            final long upperBound = request.getHookIdUpperBoundInclusive();

            List<Long> idsInRange = request.getHookIdEqualsFilters().stream()
                    .filter(hookId -> hookId >= lowerBound && hookId <= upperBound)
                    .toList();

            if (idsInRange.isEmpty()) {
                return Collections.emptyList();
            }

            return hookRepository.findByOwnerIdAndHookIdIn(id.getId(), idsInRange, page);
        }
    }
}
