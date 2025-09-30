// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.util.env;

import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
public class EnvVarWriter {

    public void appendParameter(final K6EnvProperties key, final String value) {
        final var output = String.format("::set-output name=%s::%s", key.name(), value);
        System.out.println(output);
    }
}
