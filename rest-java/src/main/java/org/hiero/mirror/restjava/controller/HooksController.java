// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.restjava.mapper.HookMapper;
import org.hiero.mirror.restjava.service.HookService;
import org.jspecify.annotations.NullMarked;
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

    @GetMapping("/{accountId}/hooks")
    ResponseEntity<HooksResponse> getHooks(
            @PathVariable long accountId,
            @RequestParam(name = "hook.id", required = false) String hookId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc")
                    @Pattern(regexp = "^(?i)(asc|desc)$", message = "must be either ''asc'' or ''desc''")
                    String order) {
        final var hooksServiceResponse = hookService.getHooks(accountId, hookId, limit, order);
        final var hooks = hookMapper.map(hooksServiceResponse);

        HooksResponse hooksResponse = new HooksResponse();
        hooksResponse.setHooks(hooks);

        if (hooks.size() == limit) {
            final long lastHookId = hooks.getLast().getHookId();
            final var links = new Links();
            links.setNext(
                    String.format("/api/v1/accounts/%d/hooks?hook.id=lt:%d&limit=%d", accountId, lastHookId, limit));

            hooksResponse.setLinks(links);
        }
        return ResponseEntity.ok(hooksResponse);
    }
}
