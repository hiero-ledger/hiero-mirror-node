// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/**
 * Retries assertion errors to handle higher level business logic failures that are probably due to timing issues across
 * independent services.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        retryFor = {AssertionError.class},
        backoff = @Backoff(delayExpression = "#{@restProperties.minBackoff.toMillis()}"),
        maxAttemptsExpression = "#{@restProperties.maxAttempts}")
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RetryAsserts {}
