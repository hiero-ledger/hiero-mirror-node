// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.fees.usage;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.LONG_SIZE;

/**
 *  Exact copy from hedera-services
 */
@SuppressWarnings("java:S6548")
public enum SingletonUsageProperties implements UsageProperties {
    USAGE_PROPERTIES;

    @Override
    public int accountAmountBytes() {
        return LONG_SIZE + BASIC_ENTITY_ID_SIZE;
    }

    @Override
    public int nftTransferBytes() {
        return LONG_SIZE + 2 * BASIC_ENTITY_ID_SIZE;
    }

    @Override
    public long legacyReceiptStorageSecs() {
        return 180;
    }
}
