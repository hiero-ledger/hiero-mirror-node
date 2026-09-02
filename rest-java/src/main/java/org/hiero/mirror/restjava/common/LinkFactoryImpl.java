// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import com.google.common.collect.Iterables;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.restjava.parameter.RequestParameter;
import org.hiero.mirror.restjava.parameter.RestJavaQueryParam;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

@Named
@RequiredArgsConstructor
@NullMarked
final class LinkFactoryImpl implements LinkFactory {

    private static final Links DEFAULT_LINKS = new Links();

    private final Map<Method, Set<String>> allowedQueryParamsCache = new ConcurrentHashMap<>();

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
        if (StringUtils.isBlank(value)) {
            // Treating blank value as an implicit eq would suppress the advancing
            // pagination bound and produce a next link identical to the current request.
            return false;
        }

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
        var allowedQueryParams = getAllowedQueryParams(request);
        var queryParams = new LinkedMultiValueMap<String, String>();

        addParamMapToQueryParams(paramsMap, paginationParamsMap, allowedQueryParams, order, queryParams);
        final var advancingBoundAdded =
                addExtractedParamsToQueryParams(sortOrders, paginationParamsMap, order, queryParams);

        // If no advancing bound was added (every sort field is eq-constrained, e.g. ?node.id=0&limit=1), the next
        // link would carry the same effective query as the current request - a next page that never advances. There
        // are no further results to page through, so return null instead of emitting a self-referential link.
        if (!advancingBoundAdded) {
            return null;
        }

        // Check if the pagination would create an empty range (e.g., gt:4 AND lt:5 with no values in between)
        // If so, return null to indicate no more results
        if (isEmptyRange(primarySort, queryParams)) {
            return null;
        }

        builder.queryParams(queryParams);
        return builder.encode().toUriString();
    }

    /**
     * Allowed query names for the matched handler: {@code @RequestParam} and {@code @RestJavaQueryParam} on
     * {@code @RequestParameter} DTOs. Cached per method so new endpoints are picked up without a global allowlist.
     */
    private Set<String> getAllowedQueryParams(HttpServletRequest request) {
        if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
                instanceof HandlerMethod handlerMethod)) {
            return Set.of();
        }

        return allowedQueryParamsCache.computeIfAbsent(
                handlerMethod.getMethod(), LinkFactoryImpl::extractQueryParamNames);
    }

    private static Set<String> extractQueryParamNames(Method method) {
        var names = new HashSet<String>();
        for (var parameter : method.getParameters()) {
            var requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                names.add(resolveQueryParamName(parameter.getName(), requestParam.value(), requestParam.name()));
                continue;
            }

            if (parameter.getAnnotation(RequestParameter.class) != null) {
                names.addAll(extractQueryParamNamesFromDto(parameter.getType()));
            }
        }

        return Set.copyOf(names);
    }

    private static Set<String> extractQueryParamNamesFromDto(Class<?> dtoClass) {
        var names = new HashSet<String>();
        for (var type = dtoClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                addQueryParamName(names, field);
            }
        }

        return names;
    }

    private static void addQueryParamName(Set<String> names, Field field) {
        if (field.isSynthetic()) {
            return;
        }

        var queryParam = field.getAnnotation(RestJavaQueryParam.class);
        if (queryParam != null) {
            names.add(resolveQueryParamName(field.getName(), queryParam.value(), queryParam.name()));
        }
    }

    private static String resolveQueryParamName(String fallback, String value, String name) {
        if (!value.isEmpty()) {
            return value;
        }
        if (!name.isEmpty()) {
            return name;
        }
        return fallback;
    }

    private static boolean isAllowedQueryParam(
            String key, Map<String, String> paginationParamsMap, Set<String> allowedQueryParams) {
        return allowedQueryParams.contains(key) || paginationParamsMap.containsKey(key);
    }

    private static boolean isSafeQueryValue(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
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
            Set<String> allowedQueryParams,
            Direction order,
            LinkedMultiValueMap<String, String> queryParams) {
        for (var entry : paramsMap.entrySet()) {
            var key = entry.getKey();
            if (!isAllowedQueryParam(key, paginationParamsMap, allowedQueryParams) || !isSafeQueryValue(key)) {
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
            // Skip if the value is null or if it contains an ISO control character.
            // Skip if it's in the same direction as the order, the new bound should come from the extracted value.
            if (!isSafeQueryValue(value) || isSameDirection(order, value)) {
                continue;
            }

            queryParams.add(entry.getKey(), value);
        }
    }

    /**
     * Appends the advancing pagination bounds (e.g. {@code node.id=gt:<lastId>}) for each sort field that is not
     * already eq-constrained.
     *
     * @return whether at least one advancing bound was added. When {@code false}, the next link would not advance
     *     past the current page and no further page exists.
     */
    @SuppressWarnings("java:S1125")
    private boolean addExtractedParamsToQueryParams(
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

        var advancingBoundAdded = false;
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
                advancingBoundAdded = true;
            }
        }

        return advancingBoundAdded;
    }
}
