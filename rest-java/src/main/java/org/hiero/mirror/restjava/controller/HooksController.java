// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.HOOK_ID;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.jooq.domain.Tables.HOOK;

import com.google.common.collect.ImmutableSortedMap;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.NumberRangeParameter;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.mapper.HookMapper;
import org.hiero.mirror.restjava.service.Bound;
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
            @RequestParam(name = "hook.id", required = false) NumberRangeParameter[] hookIds,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order) {

        final var hookIdBound = new Bound(hookIds, true, HOOK_ID, HOOK.HOOK_ID);

        final var hooksRequest = HooksRequest.builder()
                .accountId(accountId)
                .hookIds(hookIdBound)
                .limit(limit)
                .order(order)
                .build();
        final var hooksServiceResponse = hookService.getHooks(hooksRequest);
        final var hooks = hookMapper.map(hooksServiceResponse);

        final var hooksResponse = new HooksResponse();
        hooksResponse.setHooks(hooks);

        if (!hooks.isEmpty() && hooks.size() == limit) {
            final var sort = Sort.by(order, HOOK_ID);
            final var pageable = PageRequest.of(0, limit, sort);
            final var links = linkFactory.create(hooks, pageable, EXTRACTOR);
            hooksResponse.setLinks(links);
        }
        return ResponseEntity.ok(hooksResponse);
    }
}
