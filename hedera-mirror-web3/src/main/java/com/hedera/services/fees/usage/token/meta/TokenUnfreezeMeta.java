// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.usage.token.meta;

/**
 *  Exact copy from hedera-services
 * /** This is simply to get rid of code duplication with {@link TokenFreezeMeta} class. */
public class TokenUnfreezeMeta extends TokenFreezeMeta {
    public TokenUnfreezeMeta(final int bpt) {
        super(bpt);
    }
}
