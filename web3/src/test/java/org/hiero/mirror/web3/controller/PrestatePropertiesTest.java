// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrestatePropertiesTest {

    @Test
    void enabledCanBeConfigured() {
        final var properties = new PrestateProperties();
        properties.setEnabled(true);

        assertThat(properties.isEnabled()).isTrue();
    }

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

    @Test
    void stateChangeMaxPagesCanBeConfigured() {
        final var properties = new PrestateProperties();
        properties.setStateChangeMaxPages(25);

        assertThat(properties.getStateChangeMaxPages()).isEqualTo(25);
    }

    @Test
    void stateChangePageSizeCanBeConfigured() {
        final var properties = new PrestateProperties();
        properties.setStateChangePageSize(1000);

        assertThat(properties.getStateChangePageSize()).isEqualTo(1000);
    }
}
