// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.props;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ExpandedAccountId {

    private final AccountId accountId;
    private final PrivateKey privateKey;

    public ExpandedAccountId(String operatorId, String operatorKey) {
        this(AccountId.fromString(operatorId), PrivateKey.fromString(operatorKey));
    }

    public ExpandedAccountId(AccountId account) {
        this(account, null);
    }

    public PublicKey getPublicKey() {
        return privateKey != null ? privateKey.getPublicKey() : null;
    }

    @Override
    public String toString() {
        return accountId.toString();
    }
}
