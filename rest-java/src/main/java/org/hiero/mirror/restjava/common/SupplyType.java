// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

public enum SupplyType {
    TOTALCOINS,
    CIRCULATING;

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public static SupplyType of(String type) {
        if (type == null) {
            return null;
        }

        try {
            return SupplyType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid parameter: 'q'. Valid values: totalcoins, circulating");
        }
    }
}
