// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

public class TestExecutionTracker implements TestExecutionListener {

    private static final ThreadLocal<Boolean> testRunning = ThreadLocal.withInitial(() -> false);

    @Override
    public void executionStarted(final TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            testRunning.set(true);
        }
    }

    @Override
    public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult result) {
        if (testIdentifier.isTest()) {
            testRunning.set(false);
        }
    }

    public static boolean isTestRunning() {
        return testRunning.get();
    }
}
