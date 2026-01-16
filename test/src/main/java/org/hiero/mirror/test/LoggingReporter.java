// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test;

import io.cucumber.plugin.ColorAware;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import java.io.OutputStream;
import lombok.CustomLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom Cucumber plugin that uses a standard logger to log scenarios and steps.
 */
@CustomLog
public class LoggingReporter implements ConcurrentEventListener, ColorAware {

    private boolean monochrome = true;

    public LoggingReporter(OutputStream out) {
        // Parameter ignored
    }

    @Override
    public void setMonochrome(boolean monochrome) {
        this.monochrome = monochrome;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::testCaseStarted);
        publisher.registerHandlerFor(TestStepStarted.class, this::testStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::testStepFinished);
    }

    private void testCaseStarted(TestCaseStarted event) {
        var name = color(event.getTestCase().getName(), TextColor.GREEN);
        var logger = getLogger(event.getTestCase().getTestSteps().get(0));
        logger.info("Executing scenario: {}", name);
    }

    private void testStepStarted(TestStepStarted event) {
        if (event.getTestStep() instanceof PickleStepTestStep step) {
            var logger = getLogger(step);
            var name = color(step.getStep().getText(), TextColor.GREEN);
            logger.info("Executing step: {}", name);
        }
    }

    private void testStepFinished(TestStepFinished event) {
        var result = event.getResult();
        var error = result.getError();

        if (error != null) {
            var color = TextColor.GREEN;
            var logger = getLogger(event.getTestStep());
            var steps = new StringBuilder();

            // Log the passing and failed steps
            for (var step : event.getTestCase().getTestSteps()) {
                if (step instanceof PickleStepTestStep pickleStep) {
                    var stepName = pickleStep.getStep().getText();

                    if (color == TextColor.RED) {
                        color = TextColor.GRAY;
                    } else if (step == event.getTestStep()) {
                        color = TextColor.RED;
                        stepName += " <- " + error.getMessage();
                    }

                    stepName = color(stepName, color);
                    steps.append(stepName).append('\n');
                }
            }

            logger.error("Scenario failure: \n{}", steps, error);
        }
    }

    private Logger getLogger(TestStep step) {
        var location = step.getCodeLocation();
        if (location == null) {
            return log;
        }

        int index = location.lastIndexOf('(');

        if (index > 0) {
            var methodIndex = location.lastIndexOf('.', index);
            if (methodIndex > 0) {
                location = location.substring(0, methodIndex);
                return LoggerFactory.getLogger(location);
            }
        }

        return log;
    }

    private String color(String text, TextColor color) {
        if (monochrome) {
            return text;
        }
        return color.wrap(text);
    }

    private enum TextColor {
        GRAY(90),
        GREEN(32),
        RED(31);

        private static final String RESET = "\u001B[0m";
        private final String escapeSequence;

        TextColor(int code) {
            this.escapeSequence = "\u001B[" + code + "m";
        }

        String wrap(String inner) {
            return escapeSequence + inner + RESET;
        }
    }
}
