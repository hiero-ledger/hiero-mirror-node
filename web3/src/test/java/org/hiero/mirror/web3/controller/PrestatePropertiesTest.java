// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrestatePropertiesTest {

    @Test
    void maxTouchedAccountsHasDefaultValue() {
        final var properties = new PrestateProperties();

        assertThat(properties.getMaxTouchedAccounts()).isEqualTo(1000);
    }

    @Test
    void maxTouchedAccountsCanBeConfigured() {
        final var properties = new PrestateProperties();
        properties.setMaxTouchedAccounts(500);

        assertThat(properties.getMaxTouchedAccounts()).isEqualTo(500);
    }
}
