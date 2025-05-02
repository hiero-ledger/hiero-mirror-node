// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.monitor.publish.transaction.AdminKeyable;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.hiero.mirror.monitor.util.Utility;

@Data
public class TokenUpdateTransactionSupplier implements TransactionSupplier<TokenUpdateTransaction>, AdminKeyable {

    private String adminKey;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @Future
    private Instant expirationTime;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank()
    private String symbol = "HMNT";

    @NotBlank
    private String tokenId;

    private String treasuryAccountId;

    @Override
    public TokenUpdateTransaction get() {
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenMemo(Utility.getMemo("Mirror node updated test token"))
                .setTokenName(symbol + "_name")
                .setTokenSymbol(symbol)
                .setTokenId(TokenId.fromString(tokenId));

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            tokenUpdateTransaction
                    .setAdminKey(key)
                    .setFeeScheduleKey(key)
                    .setFreezeKey(key)
                    .setKycKey(key)
                    .setSupplyKey(key)
                    .setWipeKey(key);
        }
        if (treasuryAccountId != null) {
            AccountId treastury = AccountId.fromString(treasuryAccountId);
            tokenUpdateTransaction.setAutoRenewAccountId(treastury).setTreasuryAccountId(treastury);
        }

        if (expirationTime != null) {
            tokenUpdateTransaction.setExpirationTime(expirationTime);
        } else {
            tokenUpdateTransaction.setAutoRenewPeriod(autoRenewPeriod);
        }

        return tokenUpdateTransaction;
    }
}
