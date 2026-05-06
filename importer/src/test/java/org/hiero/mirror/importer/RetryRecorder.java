// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import jakarta.inject.Named;
import org.springframework.context.event.EventListener;
import org.springframework.resilience.retry.MethodRetryEvent;

// Records retry attempts made via Spring @Retryable or RetryTemplate for verification in tests
@Named
public final class RetryRecorder {

    private final Multiset<Class<? extends Throwable>> retries = ConcurrentHashMultiset.create();

    @EventListener
    public void onError(MethodRetryEvent event) {
        retries.add(event.getFailure().getClass());
    }

    public int getRetries(Class<? extends Throwable> throwableClass) {
        return retries.count(throwableClass);
    }

    public void reset() {
        retries.clear();
    }
}
