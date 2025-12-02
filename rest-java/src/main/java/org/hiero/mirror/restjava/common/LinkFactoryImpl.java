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
        servletRequestAttributes.getResponse().setHeader(HttpHeaders.LINK, LINK_HEADER.formatted(nextLink));
        return new Links().next(nextLink);
    }

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
}
