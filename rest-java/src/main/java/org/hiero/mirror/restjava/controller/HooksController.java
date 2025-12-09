// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static java.lang.Long.MAX_VALUE;
import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.HOOK_ID;
import static org.hiero.mirror.restjava.common.Constants.KEY;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MAX_REPEATED_QUERY_PARAMETERS;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import com.google.common.collect.ImmutableSortedMap;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.rest.model.HookStorage;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.rest.model.HooksStorageResponse;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.jooq.domain.tables.HookStorageChange;
import org.hiero.mirror.restjava.mapper.HookMapper;
import org.hiero.mirror.restjava.mapper.HookStorageMapper;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.parameter.NumberRangeParameter;
import org.hiero.mirror.restjava.parameter.SlotRangeParameter;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.HookService;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RequestMapping("/api/v1/accounts/{ownerId}/hooks")
@RequiredArgsConstructor
@RestController
final class HooksController {

    private static final int KEY_BYTE_LENGTH = 32;
    private static final byte[] MIN_KEY_BYTES = new byte[KEY_BYTE_LENGTH]; // A 32-byte array of 0x00
    private static final byte[] MAX_KEY_BYTES;

    private static final Function<Hook, Map<String, String>> HOOK_EXTRACTOR =
            hook -> ImmutableSortedMap.of(HOOK_ID, hook.getHookId().toString());

    private static final Function<HookStorage, Map<String, String>> HOOK_STORAGE_EXTRACTOR =
            hook -> ImmutableSortedMap.of(KEY, hook.getKey());

    static {
        MAX_KEY_BYTES = new byte[KEY_BYTE_LENGTH];
        Arrays.fill(MAX_KEY_BYTES, (byte) 0xFF); // A 32-byte array of 0xFF
    }

    private final HookService hookService;
    private final HookMapper hookMapper;
    private final HookStorageMapper hookStorageMapper;
    private final LinkFactory linkFactory;

    @GetMapping
    ResponseEntity<HooksResponse> getHooks(
            @PathVariable EntityIdParameter ownerId,
            @RequestParam(defaultValue = "", name = HOOK_ID, required = false)
                    @Size(max = MAX_REPEATED_QUERY_PARAMETERS)
                    NumberRangeParameter[] hookId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order) {

        final var hooksRequest = hooksRequest(ownerId, hookId, limit, order);
        final var hooksServiceResponse = hookService.getHooks(hooksRequest);
        final var hooks = hookMapper.map(hooksServiceResponse);

        final var sort = Sort.by(order, HOOK_ID);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(hooks, pageable, HOOK_EXTRACTOR);

        final var response = new HooksResponse();
        response.setHooks(hooks);
        response.setLinks(links);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{hookId}/storage")
    ResponseEntity<HooksStorageResponse> getHookStorage(
            @PathVariable EntityIdParameter ownerId,
            @PathVariable @Min(0) long hookId,
            @RequestParam(name = KEY, required = false, defaultValue = "") @Size(max = MAX_REPEATED_QUERY_PARAMETERS)
                    List<SlotRangeParameter> keys,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Direction order) {

        final var request = hookStorageChangeRequest(ownerId, hookId, keys, timestamps, limit, order);
        final var hookStorageResult = hookService.getHookStorage(request);
        final var hookStorage = hookStorageMapper.map(hookStorageResult.storage());

        final var sort = Sort.by(order, KEY);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(hookStorage, pageable, HOOK_STORAGE_EXTRACTOR);

        final var hookStorageResponse = new HooksStorageResponse();
        hookStorageResponse.setHookId(hookId);
        hookStorageResponse.setLinks(links);
        hookStorageResponse.setOwnerId(hookStorageResult.ownerId().toString());
        hookStorageResponse.setStorage(hookStorage);

        return ResponseEntity.ok(hookStorageResponse);
    }

    private HooksRequest hooksRequest(
            EntityIdParameter ownerId, NumberRangeParameter[] hookIdFilters, int limit, Sort.Direction order) {
        final var hookIds = new TreeSet<Long>();
        long lowerBound = 0L; // The most restrictive lower bound (max of all gt/gte)
        long upperBound = MAX_VALUE; // The most restrictive upper bound (min of all lt/lte)

        for (final var hookIdFilter : hookIdFilters) {
            if (hookIdFilter.operator() == RangeOperator.EQ) {
                hookIds.add(hookIdFilter.value());
            } else if (hookIdFilter.hasLowerBound()) {
                lowerBound = Math.max(lowerBound, hookIdFilter.getInclusiveValue());
            } else if (hookIdFilter.hasUpperBound()) {
                upperBound = Math.min(upperBound, hookIdFilter.getInclusiveValue());
            }
        }

        return HooksRequest.builder()
                .hookIds(hookIds)
                .lowerBound(lowerBound)
                .ownerId(ownerId)
                .limit(limit)
                .order(order)
                .upperBound(upperBound)
                .build();
    }

    private HookStorageRequest hookStorageChangeRequest(
            EntityIdParameter ownerId,
            long hookId,
            List<SlotRangeParameter> keys,
            TimestampParameter[] timestamps,
            int limit,
            Direction order) {
        final var keyFilters = new ArrayList<byte[]>();

        var lowerBound = MIN_KEY_BYTES;
        var upperBound = MAX_KEY_BYTES;

        for (final var key : keys) {
            final byte[] value = key.value();

            if (key.hasLowerBound()) {
                if (key.operator() == RangeOperator.EQ) {
                    keyFilters.add(value);
                } else if (Arrays.compareUnsigned(value, lowerBound) > 0) {
                    lowerBound = value;
                }
            } else if (key.hasUpperBound()) {
                if (Arrays.compareUnsigned(value, upperBound) < 0) {
                    upperBound = value;
                }
            }
        }

        final var bound = Bound.of(timestamps, TIMESTAMP, HookStorageChange.HOOK_STORAGE_CHANGE.CONSENSUS_TIMESTAMP);

        return HookStorageRequest.builder()
                .hookId(hookId)
                .keys(keyFilters)
                .limit(limit)
                .keyLowerBound(lowerBound)
                .keyUpperBound(upperBound)
                .order(order)
                .ownerId(ownerId)
                .timestamp(bound)
                .build();
    }
}
