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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.rest.model.HookStorage;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.rest.model.HooksStorageResponse;
import org.hiero.mirror.restjava.common.EntityIdNumParameter;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.NumberRangeParameter;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.jooq.domain.tables.FileData;
import org.hiero.mirror.restjava.mapper.HookMapper;
import org.hiero.mirror.restjava.mapper.HookStorageMapper;
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
import org.web3j.utils.Numeric;

@NullMarked
@RequestMapping("/api/v1/accounts/{ownerId}/hooks")
@RequiredArgsConstructor
@RestController
final class HooksController {

    private static final Function<Hook, Map<String, String>> HOOK_EXTRACTOR =
            hook -> ImmutableSortedMap.of(HOOK_ID, hook.getHookId().toString());

    private static final Function<HookStorage, Map<String, String>> HOOK_STORAGE_EXTRACTOR =
            hook -> ImmutableSortedMap.of(KEY, hook.getKey());

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
            @PathVariable long hookId,
            @RequestParam(name = KEY, required = false) @Size(max = MAX_REPEATED_QUERY_PARAMETERS)
                    List<
                                    @Pattern(
                                            regexp = "^((eq|gt|gte|lt|lte):)?(0x)?[0-9a-fA-F]{1,64}$",
                                            message = "Key must be in format <hex> or <op>:<64 or less char hex>")
                                    String>
                            keys,
            @RequestParam(name = TIMESTAMP, required = false) @Size(max = 2) TimestampParameter[] timestamps,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Direction order) {

        final var request = hookStorageChangeRequest(ownerId, hookId, keys, timestamps, limit, order);

        Collection<org.hiero.mirror.common.domain.hook.HookStorage> response;
        if (timestamps == null || timestamps.length == 0) {
            response = hookService.getHookStorage(request);
        } else {
            response = hookService.getHookStorageChange(request);
        }
        final var hookStorage = hookStorageMapper.map(response);

        final var hookStorageResponse = new HooksStorageResponse();

        hookStorageResponse.setHookId(hookId);
        hookStorageResponse.setOwnerId(((EntityIdNumParameter) ownerId).id().toString());
        hookStorageResponse.setStorage(hookStorage);

        final var sort = Sort.by(order, KEY);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(hookStorage, pageable, HOOK_STORAGE_EXTRACTOR);
        hookStorageResponse.setLinks(links);

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
            List<String> keys,
            TimestampParameter[] timestamps,
            int limit,
            Direction order) {
        final Collection<String> keyFilters = new TreeSet<>();
        var lowerBound = BigInteger.ZERO;
        var upperBound = BigInteger.valueOf(MAX_VALUE);

        if (keys != null) {
            for (final var key : keys) {
                final var parts = key.split(":", 2);
                if (parts.length < 2) {
                    keyFilters.add(normalizeHexKey(key)); // operator is not specified - assume equals
                } else {
                    final var operator = parts[0];
                    final var hex = normalizeHexKey(parts[1]);

                    switch (operator) {
                        case "eq" -> keyFilters.add(hex);
                        case "gt", "gte" -> lowerBound = lowerBound.max(getInclusiveValue(operator, hex));
                        case "lt", "lte" -> upperBound = upperBound.min(getInclusiveValue(operator, hex));
                        default -> throw new IllegalStateException("Unsupported value for operator: " + operator);
                    }
                }
            }
        }

        final var bound = timestampBound(timestamps);
        final var timestampLowerBound = bound.getAdjustedLowerRangeValue();
        final var timestampUpperBound = bound.adjustUpperBound();

        return HookStorageRequest.builder()
                .hookId(hookId)
                .keys(keyFilters)
                .limit(limit)
                .keyLowerBound(Numeric.hexStringToByteArray(zeroPadHex(lowerBound)))
                .keyUpperBound(Numeric.hexStringToByteArray(zeroPadHex(upperBound)))
                .order(order)
                .ownerId(ownerId)
                .timestampLowerBound(timestampLowerBound)
                .timestampUpperBound(timestampUpperBound)
                .build();
    }

    public String normalizeHexKey(String hexValue) {
        var hex = hexValue.replaceFirst("^(0x|0X)", ""); // strip 0x if present
        if (hex.length() < 64) {
            hex = String.format("%064x", new BigInteger(hex, 16)); // left-pad with zeros
        }
        return hex;
    }

    public String zeroPadHex(BigInteger value) {
        return String.format("%064x", value); // left-pad with zeros
    }

    public BigInteger getInclusiveValue(String operator, String hexValue) {
        final var value = new BigInteger(hexValue, 16);

        if (operator.equals("gt")) {
            return value.add(BigInteger.ONE);
        } else if (operator.equals("lt")) {
            return value.subtract(BigInteger.ONE);
        } else {
            return value;
        }
    }

    private Bound timestampBound(TimestampParameter[] timestamp) {
        if (timestamp == null || timestamp.length == 0) {
            return Bound.EMPTY;
        }

        for (int i = 0; i < timestamp.length; ++i) {
            final var param = timestamp[i];
            if (param.operator() == RangeOperator.EQ) {
                timestamp[i] = new TimestampParameter(RangeOperator.LTE, param.value());
            }
        }

        return new Bound(timestamp, false, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
    }
}
