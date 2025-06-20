// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import lombok.Getter;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

public class TestExecutionTracker implements TestExecutionListener {

    @Getter
    private static boolean testRunning;

    @Override
    public void executionStarted(final TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            testRunning = true;
        }
    }

    @Override
    public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult result) {
        if (testIdentifier.isTest()) {
            testRunning = false;
        }
    }
}
