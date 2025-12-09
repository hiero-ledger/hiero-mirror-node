// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.util.CommonUtils;
import org.junit.jupiter.api.Test;

final class ContractResultTest {

    @Test
    void setBloom() {
        final var contractResult = new ContractResult();

        // when, then
        final byte[] bloom = CommonUtils.nextBytes(256);
        contractResult.setBloom(bloom);
        assertThat(contractResult.getBloom()).isEqualTo(bloom);

        // when, then
        contractResult.setBloom(new byte[256]);
        assertThat(contractResult.getBloom()).isEmpty();
    }
}
