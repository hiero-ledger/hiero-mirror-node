// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.common;

import com.hedera.mirror.common.CommonProperties;
import org.junit.jupiter.api.BeforeAll;

public abstract class Web3UnitTest {
    @BeforeAll
    static void setUp() {
        new CommonProperties().init();
    }
}
