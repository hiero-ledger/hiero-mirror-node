// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class GlobalTestSetup implements LauncherSessionListener, TestExecutionListener {
    private CommonProperties originalCommonProperties;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        session.getLauncher().registerTestExecutionListeners(this);
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        // Only init once
        // Other modules with their own testPlanExecutionStarted handler and dependent on common / CommonProperties
        // run before common module's handler. So this will be called twice with the first from the other module's
        // global set up if exists.
        try {
            CommonProperties.getInstance();
        } catch (IllegalStateException ex) {
            new CommonProperties().init();
        } finally {
            var commonProperties = CommonProperties.getInstance();

            originalCommonProperties = new CommonProperties();
            originalCommonProperties.setShard(commonProperties.getShard());
            originalCommonProperties.setRealm(commonProperties.getRealm());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        var commonProperties = CommonProperties.getInstance();
        commonProperties.setShard(originalCommonProperties.getShard());
        commonProperties.setRealm(originalCommonProperties.getRealm());
    }
}
