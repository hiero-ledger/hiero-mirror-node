// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.hiero.mirror.monitor.util.Utility;

@Data
public class TokenMintTransactionSupplier implements TransactionSupplier<TokenMintTransaction> {

    @Min(1)
    private long amount = 1;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotNull
    private String metadata = StringUtils.EMPTY;

    @Min(14)
    private int metadataSize = 16;

    @NotBlank
    private String tokenId;

    @NotNull
    private TokenType type = TokenType.FUNGIBLE_COMMON;

    @Override
    public TokenMintTransaction get() {

        TokenMintTransaction transaction = new TokenMintTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenId(TokenId.fromString(tokenId));

        if (type == TokenType.NON_FUNGIBLE_UNIQUE) {
            for (int i = 0; i < amount; i++) {
                transaction.addMetadata(
                        !metadata.isEmpty()
                                ? metadata.getBytes(StandardCharsets.UTF_8)
                                : Utility.generateMessage(metadataSize));
            }
        } else {
            transaction.setAmount(amount);
        }

        return transaction;
    }
}
