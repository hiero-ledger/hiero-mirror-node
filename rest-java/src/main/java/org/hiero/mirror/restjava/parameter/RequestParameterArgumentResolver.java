// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import jakarta.inject.Named;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
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
@Named
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
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory)
            throws Exception {

        final var parameterType = parameter.getParameterType();

        // Get cached metadata (computed once per DTO class)
        final var metadata = getMetadata(parameterType);

        // Get path variables from request attributes
        @SuppressWarnings("unchecked")
        final var pathVariables = (Map<String, String>) webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);

        // Create instance using default constructor - same as Spring's @ModelAttribute
        Object attribute = parameterType.getDeclaredConstructor().newInstance();

        // Collect property values from annotations - same as Spring's form binding
        MutablePropertyValues propertyValues = new MutablePropertyValues();

        // Process path parameters
        for (final var entry : metadata.pathParams.entrySet()) {
            processPathParam(entry.getKey(), entry.getValue(), propertyValues, pathVariables);
        }

        // Process query parameters
        for (final var entry : metadata.queryParams.entrySet()) {
            processQueryParam(entry.getKey(), entry.getValue(), propertyValues, webRequest);
        }

        // Create WebDataBinder and bind - exactly like Spring's @ModelAttribute
        if (binderFactory == null) {
            throw new IllegalStateException("WebDataBinderFactory is required");
        }
        String objectName = parameterType.getSimpleName();
        WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, objectName);
        binder.initDirectFieldAccess();
        binder.bind(propertyValues);

        validate(binder, parameterType, attribute);

        return attribute;
    }

    private void validate(WebDataBinder binder, Class<?> parameterType, Object attribute) throws BindException {
        // Validate - same as Spring's validation
        binder.validate();

        // Throw BindException if there are validation errors (same as Spring)
        if (binder.getBindingResult().hasErrors()) {
            // Replace field names with annotation parameter names in errors
            var bindingResult = binder.getBindingResult();
            var modifiedErrors = bindingResult.getAllErrors().stream()
                    .map(error -> {
                        if (error instanceof FieldError fieldError) {
                            var paramName = getParameterName(parameterType, fieldError.getField());
                            return new FieldError(
                                    fieldError.getObjectName(),
                                    paramName, // Use annotation name instead of field name
                                    fieldError.getRejectedValue(),
                                    fieldError.isBindingFailure(),
                                    fieldError.getCodes(),
                                    fieldError.getArguments(),
                                    fieldError.getDefaultMessage());
                        }
                        return error;
                    })
                    .toList();

            var modifiedResult = new BeanPropertyBindingResult(attribute, bindingResult.getObjectName());
            modifiedErrors.forEach(modifiedResult::addError);
            throw new BindException(modifiedResult);
        }
    }

    /**
     * Get or compute binding metadata for a DTO class. Cached to avoid reflection on every request - same pattern as
     * Spring's internal caching.
     */
    private BindingMetadata getMetadata(Class<?> clazz) {
        return metadataCache.computeIfAbsent(clazz, c -> {
            Map<Field, RestJavaQueryParam> queryParams = new LinkedHashMap<>();
            Map<Field, RestJavaPathParam> pathParams = new LinkedHashMap<>();

            for (final var field : c.getDeclaredFields()) {
                // Cache @QueryParam annotations
                final var queryParam = field.getAnnotation(RestJavaQueryParam.class);
                if (queryParam != null) {
                    queryParams.put(field, queryParam);
                }

                // Cache @PathParam annotations
                final var pathParam = field.getAnnotation(RestJavaPathParam.class);
                if (pathParam != null) {
                    pathParams.put(field, pathParam);
                }
            }
            return new BindingMetadata(
                    Collections.unmodifiableMap(queryParams), Collections.unmodifiableMap(pathParams));
        });
    }

    private void processPathParam(
            Field field,
            RestJavaPathParam annotation,
            MutablePropertyValues propertyValues,
            @Nullable Map<String, String> pathVariables) {

        String variableName = extractName(field, annotation.value(), annotation.name());
        String value = pathVariables != null ? pathVariables.get(variableName) : null;

        if (value == null && annotation.required()) {
            throw new IllegalArgumentException("Missing required path variable: " + variableName);
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
        final var paramValues = webRequest.getParameterValues(paramName);

        // Handle missing or empty values
        final var resolvedValues = resolveParameterValues(paramValues, paramName, annotation);
        if (resolvedValues == null) {
            return; // No value, not required - skip
        }

        // Validate and add to property values
        validateAndAddParameter(field, paramName, resolvedValues, propertyValues);
    }

    private String @Nullable [] resolveParameterValues(
            String @Nullable [] paramValues, String paramName, RestJavaQueryParam annotation) {
        if (hasValue(paramValues)) {
            return paramValues;
        }

        return handleMissingValue(paramName, annotation);
    }

    private boolean hasValue(String @Nullable [] paramValues) {
        return paramValues != null && paramValues.length > 0 && !StringUtils.isBlank(paramValues[0]);
    }

    private String @Nullable [] handleMissingValue(String paramName, RestJavaQueryParam annotation) {
        if (!annotation.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
            return new String[] {annotation.defaultValue()};
        }

        if (annotation.required()) {
            throw new IllegalArgumentException("Missing required request parameter: " + paramName);
        }

        return null; // No value, not required
    }

    private void validateAndAddParameter(
            Field field, String paramName, String[] paramValues, MutablePropertyValues propertyValues) {
        boolean isMultiValue = field.getType().isArray() || Collection.class.isAssignableFrom(field.getType());

        if (!isMultiValue && paramValues.length > 1) {
            throw new IllegalArgumentException("Only a single instance is supported for " + paramName);
        }

        // Add to property values - WebDataBinder will handle type conversion
        Object valueToSet = isMultiValue ? paramValues : paramValues[0];
        propertyValues.add(field.getName(), valueToSet);
    }

    /**
     * Gets the parameter name for a field from cached metadata. Returns the annotation name if present, otherwise the
     * field name.
     */
    private String getParameterName(Class<?> dtoClass, String fieldName) {
        final var metadata = getMetadata(dtoClass);

        // Check query params
        for (final var entry : metadata.queryParams.entrySet()) {
            if (entry.getKey().getName().equals(fieldName)) {
                return extractName(
                        entry.getKey(),
                        entry.getValue().value(),
                        entry.getValue().name());
            }
        }

        // Check path params
        for (final var entry : metadata.pathParams.entrySet()) {
            if (entry.getKey().getName().equals(fieldName)) {
                return extractName(
                        entry.getKey(),
                        entry.getValue().value(),
                        entry.getValue().name());
            }
        }

        return fieldName;
    }

    /**
     * Metadata about parameter bindings for a DTO class. Cached to avoid reflection on every request. Immutable record
     * for thread safety.
     */
    private record BindingMetadata(
            Map<Field, RestJavaQueryParam> queryParams, Map<Field, RestJavaPathParam> pathParams) {}
}
