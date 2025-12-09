// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KeyValueWrapper {
    public enum KeyValueType {
        INVALID_KEY,
        INHERIT_ACCOUNT_KEY,
        CONTRACT_ID,
        DELEGATABLE_CONTRACT_ID,
        ED25519,
        ECDSA_SECPK256K1
    }
}
