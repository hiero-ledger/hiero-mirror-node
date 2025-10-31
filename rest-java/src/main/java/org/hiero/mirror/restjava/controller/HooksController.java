// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.HOOK_ID;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MAX_REPEATED_QUERY_PARAMETERS;

import com.google.common.collect.ImmutableSortedMap;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.NumberRangeParameter;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.mapper.HookMapper;
import org.hiero.mirror.restjava.service.HookService;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@RestController
final class HooksController {
    private final HookService hookService;
    private final HookMapper hookMapper;
    private final LinkFactory linkFactory;

    private static final Function<Hook, Map<String, String>> EXTRACTOR =
            hook -> ImmutableSortedMap.of(HOOK_ID, hook.getHookId().toString());

    @GetMapping("/{accountId}/hooks")
    ResponseEntity<HooksResponse> getHooks(
            @PathVariable EntityIdParameter accountId,
            @RequestParam(name = HOOK_ID, required = false) @Size(max = MAX_REPEATED_QUERY_PARAMETERS)
                    List<NumberRangeParameter> hookIdFilters,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order) {
        final var hooksServiceRequest = prepareHooksServiceRequest(accountId, hookIdFilters, limit, order);

        final var hooksServiceResponse = hookService.getHooks(hooksServiceRequest);
        final var hooks = hookMapper.map(hooksServiceResponse);

        final var hooksControllerResponse = new HooksResponse();
        hooksControllerResponse.setHooks(hooks);

        final var sort = Sort.by(order, HOOK_ID);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(hooks, pageable, EXTRACTOR);
        hooksControllerResponse.setLinks(links);

        return ResponseEntity.ok(hooksControllerResponse);
    }

    private HooksRequest prepareHooksServiceRequest(
            EntityIdParameter accountId, List<NumberRangeParameter> hookIdFilters, int limit, Sort.Direction order) {

        if (hookIdFilters == null || hookIdFilters.isEmpty()) {
            return HooksRequest.builder()
                    .ownerId(accountId)
                    .hookIdEqualsFilters(Collections.emptyList())
                    .hookIdLowerBoundInclusive(null)
                    .hookIdUpperBoundInclusive(null)
                    .limit(limit)
                    .order(order)
                    .build();
        }

        Collection<Long> equalHookIds = new ArrayList<>();
        Long gte = null; // The most restrictive lower bound (max of all gt/gte)
        Long lte = null; // The most restrictive upper bound (min of all lt/lte)

        for (NumberRangeParameter param : hookIdFilters) {
            RangeOperator operator = param.operator();

            if (operator == RangeOperator.EQ) {
                equalHookIds.add(param.value());

            } else if (param.hasLowerBound()) { // gt, gte
                long inclusiveValue = (operator == RangeOperator.GT) ? param.value() + 1 : param.value();
                if (gte == null || inclusiveValue > gte) {
                    gte = inclusiveValue;
                }

            } else if (param.hasUpperBound()) { // lt, lte
                long inclusiveValue = (operator == RangeOperator.LT) ? param.value() - 1 : param.value();
                if (lte == null || inclusiveValue < lte) {
                    lte = inclusiveValue;
                }
            }
        }

        // Apply defaults if a partial range is given
        if (gte != null || lte != null) {
            if (gte == null) {
                gte = 0L;
            }
            if (lte == null) {
                lte = Long.MAX_VALUE;
            }
        }

        return HooksRequest.builder()
                .hookIdLowerBoundInclusive(gte)
                .hookIdUpperBoundInclusive(lte)
                .ownerId(accountId)
                .hookIdEqualsFilters(equalHookIds)
                .limit(limit)
                .order(order)
                .build();
    }
}
