// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

public final class TestExecutionTracker implements TestExecutionListener {

    private static final AtomicInteger runningTestsCounter = new AtomicInteger(0);

    @Override
    public void executionStarted(final TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            runningTestsCounter.incrementAndGet();
        }
    }

    @Override
    public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult result) {
        if (testIdentifier.isTest()) {
            runningTestsCounter.decrementAndGet();
        }
    }

    public static boolean isTestRunning() {
        return runningTestsCounter.get() > 0;
    }
}
