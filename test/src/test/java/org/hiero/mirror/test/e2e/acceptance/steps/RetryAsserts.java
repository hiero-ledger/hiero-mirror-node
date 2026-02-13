// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Retries assertion errors to handle higher level business logic failures that are probably due to timing issues across
 * independent services.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        includes = {
            AssertionError.class,
            HttpClientErrorException.class,
            PrecheckStatusException.class,
            ReceiptStatusException.class
        },
        delayString = "#{@restProperties.minBackoff.toMillis()}",
        maxRetriesString = "#{@restProperties.maxAttempts}")
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RetryAsserts {}
