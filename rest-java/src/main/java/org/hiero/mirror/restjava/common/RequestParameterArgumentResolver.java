// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Resolves controller method parameters annotated with {@link RequestParameter}. Uses Spring's WebDataBinder for
 * binding and validation, exactly like Spring's built-in @RequestParam resolver. The only difference: annotations are
 * on DTO fields instead of method parameters.
 */
@Component
@RequiredArgsConstructor
public class RequestParameterArgumentResolver implements HandlerMethodArgumentResolver {

    // Cache reflection metadata to avoid repeated lookups - same pattern as Spring
    private final Map<Class<?>, BindingMetadata> metadataCache = new ConcurrentHashMap<>();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestParameter.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory)
            throws Exception {

        Class<?> parameterType = parameter.getParameterType();

        // Get cached metadata (computed once per DTO class)
        BindingMetadata metadata = getMetadata(parameterType);

        // Get path variables from request attributes
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);

        // Create instance using default constructor - same as Spring's @ModelAttribute
        Object attribute = parameterType.getDeclaredConstructor().newInstance();

        // Collect property values from annotations - same as Spring's form binding
        MutablePropertyValues propertyValues = new MutablePropertyValues();

        // Process path parameters
        for (Map.Entry<Field, RestJavaPathParam> entry : metadata.pathParams.entrySet()) {
            processPathParam(entry.getKey(), entry.getValue(), propertyValues, pathVariables);
        }

        // Process query parameters
        for (Map.Entry<Field, RestJavaQueryParam> entry : metadata.queryParams.entrySet()) {
            processQueryParam(entry.getKey(), entry.getValue(), propertyValues, webRequest);
        }

        // Create WebDataBinder and bind - exactly like Spring's @ModelAttribute
        String objectName = parameterType.getSimpleName();
        WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, objectName);
        binder.bind(propertyValues);

        // Validate - same as Spring's validation
        binder.validate();

        // Throw BindException if there are validation errors (same as Spring)
        if (binder.getBindingResult().hasErrors()) {
            throw new BindException(binder.getBindingResult());
        }

        return attribute;
    }

    /**
     * Get or compute binding metadata for a DTO class. Cached to avoid reflection on every request - same pattern as
     * Spring's internal caching.
     */
    private BindingMetadata getMetadata(Class<?> clazz) {
        return metadataCache.computeIfAbsent(clazz, c -> {
            BindingMetadata metadata = new BindingMetadata();

            for (Field field : c.getDeclaredFields()) {
                // Cache @QueryParam annotations
                RestJavaQueryParam queryParam = field.getAnnotation(RestJavaQueryParam.class);
                if (queryParam != null) {
                    metadata.queryParams.put(field, queryParam);
                }

                // Cache @PathParam annotations
                RestJavaPathParam pathParam = field.getAnnotation(RestJavaPathParam.class);
                if (pathParam != null) {
                    metadata.pathParams.put(field, pathParam);
                }
            }
            return metadata;
        });
    }

    private void processPathParam(
            Field field,
            RestJavaPathParam annotation,
            MutablePropertyValues propertyValues,
            Map<String, String> pathVariables) {

        String variableName = annotation.value().isEmpty() ? annotation.name() : annotation.value();
        if (variableName.isEmpty()) {
            variableName = field.getName();
        }

        String value = pathVariables != null ? pathVariables.get(variableName) : null;

        if (value == null && annotation.required()) {
            throw new IllegalArgumentException("Missing required path variable: " + variableName);
        }

        if (value != null) {
            // Add to property values - WebDataBinder will handle type conversion
            propertyValues.add(field.getName(), value);
        }
    }

    private void processQueryParam(
            Field field,
            RestJavaQueryParam annotation,
            MutablePropertyValues propertyValues,
            NativeWebRequest webRequest) {

        String paramName = annotation.value().isEmpty() ? annotation.name() : annotation.value();
        if (paramName.isEmpty()) {
            paramName = field.getName();
        }

        // Extract parameter values - same logic as RequestParamMethodArgumentResolver
        String[] paramValues = webRequest.getParameterValues(paramName);

        // Handle default value if no parameter provided
        if ((paramValues == null || paramValues.length == 0 || StringUtils.isBlank(paramValues[0]))) {
            if (!annotation.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
                paramValues = new String[] {annotation.defaultValue()};
            } else if (annotation.required()) {
                throw new IllegalArgumentException("Missing required request parameter: " + paramName);
            } else {
                // No value, not required - skip (let @Builder.Default handle it)
                return;
            }
        }

        // Add to property values - WebDataBinder will handle type conversion and arrays/collections
        // For arrays or Collections (List, Set, etc.), pass all values; otherwise just the first
        Object valueToSet = (field.getType().isArray() || Collection.class.isAssignableFrom(field.getType()))
                ? paramValues
                : paramValues[0];
        propertyValues.add(field.getName(), valueToSet);
    }

    /**
     * Metadata about parameter bindings for a DTO class. Cached to avoid reflection on every request.
     */
    private static class BindingMetadata {
        final Map<Field, RestJavaQueryParam> queryParams = new HashMap<>();
        final Map<Field, RestJavaPathParam> pathParams = new HashMap<>();
    }
}
