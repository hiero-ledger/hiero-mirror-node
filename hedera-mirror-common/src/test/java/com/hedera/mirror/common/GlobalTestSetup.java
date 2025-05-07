// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class GlobalTestSetup implements LauncherSessionListener, TestExecutionListener {

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
        }
    }
}
