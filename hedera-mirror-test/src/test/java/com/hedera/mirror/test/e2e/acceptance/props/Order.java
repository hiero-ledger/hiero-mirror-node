// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.test.e2e.acceptance.props;

public enum Order {
    ASC,
    DESC;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
