// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;

@Named
@NullMarked
@RequiredArgsConstructor
final class HookServiceImpl implements HookService {

    private static final Pattern HOOK_ID_FORMAT = Pattern.compile("^(eq|gt|gte|lt|lte):(\\d+)$");

    private final HookRepository hookRepository;

    /**
     * Retrieves all hooks (active or deleted) of a given owner.
     *
     * @param ownerId The ID of the owner account.
     * @return List of all hooks for the owner.
     */
    @Override
    public List<Hook> getHooks(long ownerId, String hookIdFilter, int limit, String order) {
        final var pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        final boolean ascending = "asc".equalsIgnoreCase(order);

        if (hookIdFilter == null || hookIdFilter.isBlank()) {
            return ascending
                    ? hookRepository.findByOwnerIdOrderByHookIdAsc(ownerId, pageable)
                    : hookRepository.findByOwnerIdOrderByHookIdDesc(ownerId, pageable);
        }

        final var matcher = HOOK_ID_FORMAT.matcher(hookIdFilter);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid hook.id format. Must be one of eq:, gt:, gte:, lt:, lte: followed by a number.");
        }

        final var operator = matcher.group(1);
        final long value = Long.parseLong(matcher.group(2));

        return switch (operator) {
            case "eq" -> {
                Hook h = hookRepository.findByOwnerIdAndHookId(ownerId, value);
                yield h == null ? List.of() : List.of(h);
            }
            case "lt" ->
                ascending
                        ? hookRepository.findByOwnerIdAndHookIdLessThanOrderByHookIdAsc(ownerId, value, pageable)
                        : hookRepository.findByOwnerIdAndHookIdLessThanOrderByHookIdDesc(ownerId, value, pageable);
            case "lte" ->
                ascending
                        ? hookRepository.findByOwnerIdAndHookIdLessThanOrderByHookIdAsc(ownerId, value + 1, pageable)
                        : hookRepository.findByOwnerIdAndHookIdLessThanOrderByHookIdDesc(ownerId, value + 1, pageable);
            case "gt" ->
                ascending
                        ? hookRepository.findByOwnerIdAndHookIdGreaterThanOrderByHookIdAsc(ownerId, value, pageable)
                        : hookRepository.findByOwnerIdAndHookIdGreaterThanOrderByHookIdDesc(ownerId, value, pageable);
            case "gte" ->
                ascending
                        ? hookRepository.findByOwnerIdAndHookIdGreaterThanOrderByHookIdAsc(ownerId, value - 1, pageable)
                        : hookRepository.findByOwnerIdAndHookIdGreaterThanOrderByHookIdDesc(
                                ownerId, value - 1, pageable);
            default -> throw new IllegalArgumentException("Unsupported hook.id operator: " + operator);
        };
    }
}
