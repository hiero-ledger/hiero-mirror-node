// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava;

import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import java.util.Arrays;

public abstract class RestJavaIntegrationTest extends CommonIntegrationTest {

    protected EntityIdRangeParameter[] paramToArray(EntityIdRangeParameter... param) {
        return Arrays.copyOf(param, param.length);
    }
}
