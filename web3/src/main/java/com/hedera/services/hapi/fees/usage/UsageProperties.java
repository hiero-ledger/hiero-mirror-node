// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.fees.usage;

public interface UsageProperties {
    int accountAmountBytes();

    int nftTransferBytes();

    long legacyReceiptStorageSecs();
}
