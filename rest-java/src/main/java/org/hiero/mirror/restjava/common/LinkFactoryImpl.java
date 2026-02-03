// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import com.google.common.collect.Iterables;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.Links;
import org.jspecify.annotations.NullMarked;
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

    private static final Links DEFAULT_LINKS = new Links();

    @Override
    public <T> Links create(List<T> items, Pageable pageable, Function<T, Map<String, String>> extractor) {
        if (CollectionUtils.isEmpty(items) || pageable.getPageSize() > items.size()) {
            return DEFAULT_LINKS;
        }

        var servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes == null) {
            return DEFAULT_LINKS;
        }

        var request = servletRequestAttributes.getRequest();
        var lastItem = CollectionUtils.lastElement(items);
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
        if (isEmptyRange(queryParams, sortOrders)) {
            return null;
        }

        builder.queryParams(queryParams);
        return builder.toUriString();
    }

    private void addParamMapToQueryParams(
            Map<String, String[]> paramsMap,
            Map<String, String> paginationParamsMap,
            Direction order,
            LinkedMultiValueMap<String, String> queryParams) {
        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            if (!paginationParamsMap.containsKey(key)) {
                for (var value : entry.getValue()) {
                    queryParams.add(entry.getKey(), value);
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
            if (isSameDirection(order, value)) {
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
            queryParams.add(key, getOperator(order, exclusive) + ":" + value);
        }
    }

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
     * Checks if the query parameters would create an empty range (e.g., gt:4 AND lt:5).
     * This happens when the pagination link would exclude all remaining results.
     *
     * Note: This operates on HTTP query parameter strings since LinkFactory works at the HTTP level.
     * The EntityIdRangeParameter parsing happens earlier in the service layer, but by this point
     * we need to check the combined query params (original + newly added pagination bounds).
     */
    private static boolean isEmptyRange(LinkedMultiValueMap<String, String> queryParams, Sort sort) {
        // Get the primary sort field (first one in the sort order)
        var primaryField = sort.iterator().hasNext() ? sort.iterator().next().getProperty() : null;
        if (primaryField == null) {
            return false;
        }

        var values = queryParams.get(primaryField);
        if (values == null || values.isEmpty()) {
            return false;
        }

        // Compute the effective range bounds from all query parameters
        Long lower = null;
        Long upper = null;

        for (var value : values) {
            var normalized = value.toLowerCase();

            try {
                // Extract the numeric value and update bounds
                if (normalized.startsWith("gt:")) {
                    long val = Long.parseLong(value.substring(3)) + 1; // gt:4 → gte:5
                    lower = lower == null ? val : Math.max(lower, val);
                } else if (normalized.startsWith("gte:")) {
                    long val = Long.parseLong(value.substring(4));
                    lower = lower == null ? val : Math.max(lower, val);
                } else if (normalized.startsWith("lt:")) {
                    long val = Long.parseLong(value.substring(3)) - 1; // lt:5 → lte:4
                    upper = upper == null ? val : Math.min(upper, val);
                } else if (normalized.startsWith("lte:")) {
                    long val = Long.parseLong(value.substring(4));
                    upper = upper == null ? val : Math.min(upper, val);
                }
            } catch (NumberFormatException e) {
                // Skip invalid values
            }
        }

        // If we have both bounds and upper < lower, the range is empty
        return lower != null && upper != null && upper < lower;
    }
}
