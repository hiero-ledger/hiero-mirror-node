// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import org.springframework.core.Ordered;

/*
 * Invoked when all tests are finished to clean up test resources.
 */
public interface Cleanable extends Ordered {

    void clean();

    @Override
    default int getOrder() {
        return 0;
    }
}
