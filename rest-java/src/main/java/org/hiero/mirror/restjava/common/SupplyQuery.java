// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

public enum SupplyQuery {
    TOTALCOINS,
    CIRCULATING;

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public static SupplyQuery of(String supplyQuery) {
        if (supplyQuery == null) {
            return null;
        }

        try {
            return SupplyQuery.valueOf(supplyQuery.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid parameter: 'q'. Valid values: totalcoins, circulating");
        }
    }
}
