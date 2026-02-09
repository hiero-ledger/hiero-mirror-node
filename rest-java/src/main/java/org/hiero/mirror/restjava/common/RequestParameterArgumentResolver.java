// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.restjava.exception.InvalidParameterCountException;
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

        // Check for unknown query parameters and add to binding result
        addUnknownParameterErrors(metadata, webRequest, binder);

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
            Map<Field, RestJavaQueryParam> queryParams = new java.util.LinkedHashMap<>();
            Map<Field, RestJavaPathParam> pathParams = new java.util.LinkedHashMap<>();

            for (Field field : c.getDeclaredFields()) {
                // Cache @QueryParam annotations
                RestJavaQueryParam queryParam = field.getAnnotation(RestJavaQueryParam.class);
                if (queryParam != null) {
                    queryParams.put(field, queryParam);
                }

                // Cache @PathParam annotations
                RestJavaPathParam pathParam = field.getAnnotation(RestJavaPathParam.class);
                if (pathParam != null) {
                    pathParams.put(field, pathParam);
                }
            }
            return new BindingMetadata(queryParams, pathParams);
        });
    }

    private void processPathParam(
            Field field,
            RestJavaPathParam annotation,
            MutablePropertyValues propertyValues,
            Map<String, String> pathVariables) {

        String variableName = extractName(field, annotation.value(), annotation.name());
        String value = pathVariables != null ? pathVariables.get(variableName) : null;

        if (value == null && annotation.required()) {
            throw new InvalidParameterCountException("Missing required path variable: " + variableName);
        }

        if (value != null) {
            propertyValues.add(field.getName(), value);
        }
    }

    /**
     * Extracts the parameter/variable name with priority: value > name > field name. Shared logic for both query params
     * and path variables.
     */
    private String extractName(Field field, String value, String name) {
        if (!value.isEmpty()) {
            return value;
        }
        if (!name.isEmpty()) {
            return name;
        }
        return field.getName();
    }

    private void processQueryParam(
            Field field,
            RestJavaQueryParam annotation,
            MutablePropertyValues propertyValues,
            NativeWebRequest webRequest) {

        String paramName = extractName(field, annotation.value(), annotation.name());
        String[] paramValues = webRequest.getParameterValues(paramName);

        // Handle missing or empty values
        paramValues = resolveParameterValues(paramValues, paramName, annotation);
        if (paramValues == null) {
            return; // No value, not required - skip
        }

        // Validate and add to property values
        validateAndAddParameter(field, paramName, paramValues, propertyValues);
    }

    private String[] resolveParameterValues(String[] paramValues, String paramName, RestJavaQueryParam annotation) {
        boolean hasNoValue = paramValues == null || paramValues.length == 0 || StringUtils.isBlank(paramValues[0]);

        if (!hasNoValue) {
            return paramValues;
        }

        // Handle default value or required parameter
        if (!annotation.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
            return new String[] {annotation.defaultValue()};
        }

        if (annotation.required()) {
            throw new InvalidParameterCountException("Missing required request parameter: " + paramName);
        }

        return null; // No value, not required
    }

    private void validateAndAddParameter(
            Field field, String paramName, String[] paramValues, MutablePropertyValues propertyValues) {
        boolean isMultiValue = field.getType().isArray() || Collection.class.isAssignableFrom(field.getType());

        if (!isMultiValue && paramValues.length > 1) {
            throw new InvalidParameterCountException("Only a single instance is supported for " + paramName);
        }

        // Add to property values - WebDataBinder will handle type conversion
        Object valueToSet = isMultiValue ? paramValues : paramValues[0];
        propertyValues.add(field.getName(), valueToSet);
    }

    /**
     * Validates that all query parameters in the request are known (defined in the DTO). Adds errors to the binding
     * result for any unknown parameters.
     */
    private void addUnknownParameterErrors(
            BindingMetadata metadata, NativeWebRequest webRequest, WebDataBinder binder) {
        Map<String, String[]> allParams = webRequest.getParameterMap();

        // Collect all known parameter names from annotations
        var knownParams = metadata.queryParams.values().stream()
                .map(annotation -> annotation.value().isEmpty() ? annotation.name() : annotation.value())
                .filter(name -> !name.isEmpty())
                .toList();

        // Check for unknown parameters and add errors
        for (String paramName : allParams.keySet()) {
            if (!knownParams.contains(paramName)) {
                binder.getBindingResult().reject("unknown.parameter", "Unknown query parameter: " + paramName);
            }
        }
    }

    /**
     * Metadata about parameter bindings for a DTO class. Cached to avoid reflection on every request. Immutable record
     * for thread safety.
     */
    private record BindingMetadata(
            Map<Field, RestJavaQueryParam> queryParams, Map<Field, RestJavaPathParam> pathParams) {

        BindingMetadata(Map<Field, RestJavaQueryParam> queryParams, Map<Field, RestJavaPathParam> pathParams) {
            // Make maps unmodifiable for thread safety - preserves LinkedHashMap order
            this.queryParams = Collections.unmodifiableMap(queryParams);
            this.pathParams = Collections.unmodifiableMap(pathParams);
        }
    }
}
