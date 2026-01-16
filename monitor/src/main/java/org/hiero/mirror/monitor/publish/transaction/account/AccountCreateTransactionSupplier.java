// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.account;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import jakarta.validation.constraints.Min;
import lombok.CustomLog;
import lombok.Data;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.hiero.mirror.monitor.util.Utility;

@Data
@CustomLog
public class AccountCreateTransactionSupplier implements TransactionSupplier<AccountCreateTransaction> {

    @Min(1)
    private long initialBalance = 10_000_000;

    private boolean logKeys = false;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    private boolean receiverSignatureRequired = false;

    private String publicKey;

    @Override
    public AccountCreateTransaction get() {
        return new AccountCreateTransaction()
                .setAccountMemo(Utility.getMemo("Mirror node created test account"))
                .setInitialBalance(Hbar.fromTinybars(initialBalance))
                .setKeyWithoutAlias(publicKey != null ? PublicKey.fromString(publicKey) : generateKeys())
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setReceiverSignatureRequired(receiverSignatureRequired);
    }

    private PublicKey generateKeys() {
        PrivateKey privateKey = PrivateKey.generateED25519();

        // Since these keys will never be seen again, if we want to reuse this account
        // provide an option to print them
        if (logKeys) {
            log.info("privateKey: {}", privateKey);
            log.info("publicKey: {}", privateKey.getPublicKey());
        }

        return privateKey.getPublicKey();
    }
}
