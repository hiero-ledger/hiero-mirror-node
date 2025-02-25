// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.util;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenUpdateValidator {

    private TokenUpdateValidator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static ResponseCodeEnum validate(final TransactionBody txnBody, final OptionValidator validator) {
        final TokenUpdateTransactionBody op = txnBody.getTokenUpdate();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        var validity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
        if (validity != OK) {
            return validity;
        }

        final var hasNewSymbol = !op.getSymbol().isEmpty();
        if (hasNewSymbol) {
            validity = validator.tokenSymbolCheck(op.getSymbol());
            if (validity != OK) {
                return validity;
            }
        }

        final var hasNewTokenName = !op.getName().isEmpty();
        if (hasNewTokenName) {
            validity = validator.tokenNameCheck(op.getName());
            if (validity != OK) {
                return validity;
            }
        }

        validity = checkKeys(
                op.hasAdminKey(), op.getAdminKey(),
                op.hasKycKey(), op.getKycKey(),
                op.hasWipeKey(), op.getWipeKey(),
                op.hasSupplyKey(), op.getSupplyKey(),
                op.hasFreezeKey(), op.getFreezeKey(),
                op.hasFeeScheduleKey(), op.getFeeScheduleKey(),
                op.hasPauseKey(), op.getPauseKey());

        return validity;
    }
}
