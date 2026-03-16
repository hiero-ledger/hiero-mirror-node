// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter to be automatically resolved from a DTO that contains {@link RestJavaQueryParam} and
 * {@link RestJavaPathParam} annotated fields.
 *
 * <p>Example:
 * <pre>
 * {@literal @}Value
 * {@literal @}Builder
 * public class AccountStorageRequest {
 *     {@literal @}PathParam(name = "accountId")
 *     String accountId;
 *
 *     {@literal @}QueryParam(name = "keys", required = false)
 *     String[] keys;
 *
 *     {@literal @}QueryParam(name = "limit", defaultValue = "25")
 *     {@literal @}Min(1) {@literal @}Max(1000)
 *     int limit;
 * }
 *
 * // In controller:
 * {@literal @}GetMapping("/account/{accountId}/hooks/storage")
 * ResponseEntity<?> getStorage({@literal @}RequestParameter AccountStorageRequest request) {
 *     // request is fully populated from path and query parameters
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParameter {}
