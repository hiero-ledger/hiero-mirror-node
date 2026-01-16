// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.CustomLog;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@CustomLog
@SpringBootApplication
public class TestApplication {

    @SuppressWarnings("java:S106")
    public static void main(String[] args) {
        log.info("Executing acceptance tests with args: {}", List.of(args));

        var listener = new SummaryGeneratingListener();
        var selector = selectClass("org.hiero.mirror.test.e2e.acceptance.AcceptanceTest");
        var request = LauncherDiscoveryRequestBuilder.request()
                .configurationParameters(convertArgs(args))
                .selectors(selector)
                .build();

        var launcher = LauncherFactory.create();
        launcher.discover(request);
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        var summary = listener.getSummary();
        summary.printFailuresTo(new PrintWriter(System.out));
        summary.printTo(new PrintWriter(System.out));

        if (summary.getTotalFailureCount() > 0) {
            System.exit(-1);
        }

        System.exit(0);
    }

    /**
     * JUnit only supports loading properties from junit-platform.properties on classpath or system properties. Since it
     * is difficult to pass dynamic VM arguments to containers, we manually pass program arguments to JUnit.
     */
    private static Map<String, String> convertArgs(String[] args) {
        final var argMap = new LinkedHashMap<String, String>();
        final var pattern = Pattern.compile("^-D([a-zA-Z.]+)=(.+)$");

        for (String arg : args) {
            var matcher = pattern.matcher(arg);
            if (matcher.matches()) {
                var key = matcher.group(1);
                var value = matcher.group(2);
                argMap.put(key, value);
            }
        }

        return argMap;
    }
}
