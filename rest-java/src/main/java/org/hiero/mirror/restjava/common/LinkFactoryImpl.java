// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.hiero.mirror.restjava.common.Constants.ACCOUNT_ID;
import static org.hiero.mirror.restjava.common.Constants.FILE_ID;
import static org.hiero.mirror.restjava.common.Constants.HOOK_ID;
import static org.hiero.mirror.restjava.common.Constants.KEY;
import static org.hiero.mirror.restjava.common.Constants.LIMIT;
import static org.hiero.mirror.restjava.common.Constants.NODE_ID;
import static org.hiero.mirror.restjava.common.Constants.ORDER;
import static org.hiero.mirror.restjava.common.Constants.OWNER;
import static org.hiero.mirror.restjava.common.Constants.RECEIVER_ID;
import static org.hiero.mirror.restjava.common.Constants.REGISTERED_NODE_ID;
import static org.hiero.mirror.restjava.common.Constants.REGISTERED_NODE_TYPE;
import static org.hiero.mirror.restjava.common.Constants.SENDER_ID;
import static org.hiero.mirror.restjava.common.Constants.SERIAL_NUMBER;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;
import static org.hiero.mirror.restjava.common.Constants.TOKEN_ID;

import com.google.common.collect.Iterables;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.Links;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Named
@RequiredArgsConstructor
@NullMarked
final class LinkFactoryImpl implements LinkFactory {

    private static final Set<String> ALLOWED_QUERY_PARAMS = Set.of(
            ACCOUNT_ID,
            FILE_ID,
            HOOK_ID,
            KEY,
            LIMIT,
            NODE_ID,
            ORDER,
            OWNER,
            RECEIVER_ID,
            REGISTERED_NODE_ID,
            REGISTERED_NODE_TYPE,
            SENDER_ID,
            SERIAL_NUMBER,
            TIMESTAMP,
            TOKEN_ID);

    private static final Links DEFAULT_LINKS = new Links();

    private static RangeOperator getOperator(Direction order, boolean exclusive) {
        return switch (order) {
            case ASC -> exclusive ? RangeOperator.GT : RangeOperator.GTE;
            case DESC -> exclusive ? RangeOperator.LT : RangeOperator.LTE;
        };
    }

    private static boolean isSameDirection(Direction order, String value) {
        var normalized = value.toLowerCase();
        return switch (order) {
            case ASC -> normalized.startsWith("gt:") || normalized.startsWith("gte:");
            case DESC -> normalized.startsWith("lt:") || normalized.startsWith("lte:");
        };
    }

    private static boolean containsEq(List<String> values) {
        for (var value : values) {
            if (hasEq(value)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasEq(String value) {
        var normalized = value.toLowerCase();
        return normalized.startsWith("eq:")
                || (!normalized.startsWith("gt:")
                        && !normalized.startsWith("gte:")
                        && !normalized.startsWith("lt:")
                        && !normalized.startsWith("lte:"));
    }

    /**
     * Checks if the query parameters would create an empty range (e.g., gt:4 AND lt:5). This happens when the
     * pagination link would exclude all remaining results.
     * <p>
     * Note: This operates on HTTP query parameter strings since LinkFactory works at the HTTP level. The
     * EntityIdRangeParameter parsing happens earlier in the service layer, but by this point we need to check the
     * combined query params (original + newly added pagination bounds).
     */
    private static boolean isEmptyRange(
            Sort.@Nullable Order primarySort, LinkedMultiValueMap<String, String> queryParams) {
        if (primarySort == null) {
            return false;
        }

        var primaryField = primarySort.getProperty();

        var values = queryParams.get(primaryField);
        if (values == null || values.isEmpty()) {
            return false;
        }

        // Compute the effective range bounds from all query parameters
        var lower = Long.MIN_VALUE;
        var upper = Long.MAX_VALUE;

        for (var value : values) {
            var normalized = value.toLowerCase();

            try {
                // Extract the numeric value and update bounds
                if (normalized.startsWith("gt:")) {
                    long val = Long.parseLong(value.substring(3)) + 1; // gt:4 → gte:5
                    lower = Math.max(lower, val);
                } else if (normalized.startsWith("gte:")) {
                    long val = Long.parseLong(value.substring(4));
                    lower = Math.max(lower, val);
                } else if (normalized.startsWith("lt:")) {
                    long val = Long.parseLong(value.substring(3)) - 1; // lt:5 → lte:4
                    upper = Math.min(upper, val);
                } else if (normalized.startsWith("lte:")) {
                    long val = Long.parseLong(value.substring(4));
                    upper = Math.min(upper, val);
                }
            } catch (NumberFormatException e) {
                // Skip invalid values
            }
        }

        // If upper < lower, the range is empty (e.g., gt:4 AND lt:5)
        return upper < lower;
    }

    @Override
    public <T> Links create(List<T> items, Pageable pageable, Function<T, Map<String, String>> extractor) {
        if (CollectionUtils.isEmpty(items) || pageable.getPageSize() > items.size()) {
            return DEFAULT_LINKS;
        }

        final var servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes == null || servletRequestAttributes.getResponse() == null) {
            return DEFAULT_LINKS;
        }

        var request = servletRequestAttributes.getRequest();
        var lastItem = Objects.requireNonNull(CollectionUtils.lastElement(items));
        var nextLink = createNextLink(lastItem, pageable, extractor, request);

        // If nextLink is null, it means the pagination range would be empty - no more results
        if (nextLink == null) {
            return DEFAULT_LINKS;
        }

        servletRequestAttributes.getResponse().setHeader(HttpHeaders.LINK, LINK_HEADER.formatted(nextLink));
        return new Links().next(nextLink);
    }

    @org.jspecify.annotations.Nullable
    private <T> String createNextLink(
            T lastItem, Pageable pageable, Function<T, Map<String, String>> extractor, HttpServletRequest request) {
        var sortOrders = pageable.getSort();
        var primarySort = Iterables.getFirst(sortOrders, null);
        var order = primarySort == null ? Direction.ASC : primarySort.getDirection();
        var builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        var paramsMap = request.getParameterMap();
        var paginationParamsMap = extractor.apply(lastItem);
        var queryParams = new LinkedMultiValueMap<String, String>();

        addParamMapToQueryParams(paramsMap, paginationParamsMap, order, queryParams);
        addExtractedParamsToQueryParams(sortOrders, paginationParamsMap, order, queryParams);

        // Check if the pagination would create an empty range (e.g., gt:4 AND lt:5 with no values in between)
        // If so, return null to indicate no more results
        if (isEmptyRange(primarySort, queryParams)) {
            return null;
        }

        builder.queryParams(queryParams);
        return builder.encode().toUriString();
    }

    private static boolean isAllowedQueryParam(String key, Map<String, String> paginationParamsMap) {
        return ALLOWED_QUERY_PARAMS.contains(key) || paginationParamsMap.containsKey(key);
    }

    private static boolean isSafeQueryValue(@Nullable String value) {
        if (value == null) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private void addParamMapToQueryParams(
            Map<String, String[]> paramsMap,
            Map<String, String> paginationParamsMap,
            Direction order,
            LinkedMultiValueMap<String, String> queryParams) {
        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            if (!isAllowedQueryParam(key, paginationParamsMap) || !isSafeQueryValue(key)) {
                continue;
            }

            if (!paginationParamsMap.containsKey(key)) {
                for (var value : entry.getValue()) {
                    if (isSafeQueryValue(value)) {
                        queryParams.add(key, value);
                    }
                }
            } else {
                addQueryParamToLink(entry, order, queryParams);
            }
        }
    }

    private void addQueryParamToLink(
            Entry<String, String[]> entry, Direction order, LinkedMultiValueMap<String, String> queryParams) {
        for (var value : entry.getValue()) {
            // Skip if it's in the same direction as the order, the new bound should come from the extracted value
            if (!isSafeQueryValue(value) || isSameDirection(order, value)) {
                continue;
            }

            queryParams.add(entry.getKey(), value);
        }
    }

    @SuppressWarnings("java:S1125")
    private void addExtractedParamsToQueryParams(
            Sort sort,
            Map<String, String> paginationParamsMap,
            Direction order,
            LinkedMultiValueMap<String, String> queryParams) {
        var sortEqMap = new HashMap<String, Boolean>();
        var sortList = sort.map(s -> {
                    var property = s.getProperty();
                    sortEqMap.put(property, containsEq(queryParams.getOrDefault(property, List.of())));
                    return property;
                })
                .toList();

        for (int i = 0; i < sortList.size(); i++) {
            var key = sortList.get(i);
            if (queryParams.containsKey(key) && Boolean.TRUE.equals(sortEqMap.get(key))) {
                // This query parameter has already been added with an eq
                continue;
            }

            int nextParamIndex = i + 1;
            boolean exclusive = sortList.size() > nextParamIndex ? sortEqMap.get(sortList.get(nextParamIndex)) : true;
            var value = paginationParamsMap.get(key);
            var paramValue = getOperator(order, exclusive) + ":" + value;
            if (isSafeQueryValue(paramValue)) {
                queryParams.add(key, paramValue);
            }
        }
    }
}
