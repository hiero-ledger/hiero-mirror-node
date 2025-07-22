// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import java.util.function.Consumer;
import org.hiero.mirror.common.tableusage.CsvReportGenerator;
import org.hiero.mirror.common.tableusage.MarkdownReportGenerator;
import org.hiero.mirror.common.tableusage.TestExecutionTracker;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class GlobalTestSetup implements LauncherSessionListener, TestExecutionListener {

    private final CommonProperties originalCommonProperties = new CommonProperties();

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        session.getLauncher().registerTestExecutionListeners(this, new TestExecutionTracker());
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
            var commonProperties = new CommonProperties();
            commonProperties.init();
            setPropertyFromEnv("HIERO_MIRROR_COMMON_REALM", commonProperties::setRealm);
            setPropertyFromEnv("HIERO_MIRROR_COMMON_SHARD", commonProperties::setShard);
        } finally {
            copyCommonProperties(CommonProperties.getInstance(), originalCommonProperties);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        copyCommonProperties(originalCommonProperties, CommonProperties.getInstance());
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        final var generateTableUsage = System.getenv().getOrDefault("GENERATE_TABLE_USAGE", "true");

        if (Boolean.parseBoolean(generateTableUsage)) {
            MarkdownReportGenerator.generateReport();
            CsvReportGenerator.generateReport();
        }
    }

    private void copyCommonProperties(CommonProperties src, CommonProperties dst) {
        dst.setRealm(src.getRealm());
        dst.setShard(src.getShard());
    }

    private void setPropertyFromEnv(String key, Consumer<Long> setter) {
        var value = System.getenv(key);
        if (value != null) {
            setter.accept(Long.valueOf(value));
        }
    }
}
