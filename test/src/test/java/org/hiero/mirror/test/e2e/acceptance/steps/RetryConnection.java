// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Retries HTTP client error exceptions that are probably due to connectivity issues between different services.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        retryFor = {HttpClientErrorException.class, ResourceAccessException.class},
        backoff = @Backoff(delayExpression = "#{@restProperties.minBackoff.toMillis()}"),
        maxAttemptsExpression = "#{@restProperties.maxAttempts}")
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RetryConnection {}
