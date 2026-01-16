// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import jakarta.inject.Named;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

// Records retry attempts made via Spring @Retryable or RetryTemplate for verification in tests
@Named
public final class RetryRecorder implements RetryListener {

    private final Multiset<Class<? extends Throwable>> retries = ConcurrentHashMultiset.create();

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable t) {
        retries.add(t.getClass());
    }

    public int getRetries(Class<? extends Throwable> throwableClass) {
        return retries.count(throwableClass);
    }

    public void reset() {
        retries.clear();
    }
}
