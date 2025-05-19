// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava;

import java.util.Arrays;
import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.restjava.common.EntityIdRangeParameter;

public abstract class RestJavaIntegrationTest extends CommonIntegrationTest {

    protected EntityIdRangeParameter[] paramToArray(EntityIdRangeParameter... param) {
        return Arrays.copyOf(param, param.length);
    }
}
