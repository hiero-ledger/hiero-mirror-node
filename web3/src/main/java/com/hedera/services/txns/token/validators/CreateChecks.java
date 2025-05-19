// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.token.validators;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.nftSupplyKeyCheck;
import static com.hedera.services.txns.validation.TokenListChecks.suppliesCheck;
import static com.hedera.services.txns.validation.TokenListChecks.supplyTypeCheck;
import static com.hedera.services.txns.validation.TokenListChecks.typeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;

public class CreateChecks {

    private final OptionValidator validator;

    public CreateChecks(final OptionValidator validator) {
        this.validator = validator;
    }

    public Function<TransactionBody, ResponseCodeEnum> validate() {
        return txnBody -> {
            final TokenCreateTransactionBody op = txnBody.getTokenCreation();

            var validity = validateTokenFields(op);
            if (validity != OK) {
                return validity;
            }

            validity = validateTokenTypes(op);
            if (validity != OK) {
                return validity;
            }

            validity = validateTokenSupplies(op);
            if (validity != OK) {
                return validity;
            }

            if (!op.hasTreasury()) {
                return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
            }

            validity = checkKeys(
                    op.hasAdminKey(), op.getAdminKey(),
                    op.hasKycKey(), op.getKycKey(),
                    op.hasWipeKey(), op.getWipeKey(),
                    op.hasSupplyKey(), op.getSupplyKey(),
                    op.hasFreezeKey(), op.getFreezeKey(),
                    op.hasFeeScheduleKey(), op.getFeeScheduleKey(),
                    op.hasPauseKey(), op.getPauseKey());
            if (validity != OK) {
                return validity;
            }

            if (op.getFreezeDefault() && !op.hasFreezeKey()) {
                return TOKEN_HAS_NO_FREEZE_KEY;
            }
            return validateAutoRenewAccount(op, validator);
        };
    }

    private ResponseCodeEnum validateTokenFields(final TokenCreateTransactionBody op) {
        var validity = validator.memoCheck(op.getMemo());
        if (validity != OK) {
            return validity;
        }

        validity = validator.tokenSymbolCheck(op.getSymbol());
        if (validity != OK) {
            return validity;
        }

        return validator.tokenNameCheck(op.getName());
    }

    private ResponseCodeEnum validateTokenTypes(final TokenCreateTransactionBody op) {
        var validity = typeCheck(op.getTokenType(), op.getInitialSupply(), op.getDecimals());
        if (validity != OK) {
            return validity;
        }

        return supplyTypeCheck(op.getSupplyType(), op.getMaxSupply());
    }

    private ResponseCodeEnum validateTokenSupplies(final TokenCreateTransactionBody op) {
        var validity = suppliesCheck(op.getInitialSupply(), op.getMaxSupply());
        if (validity != OK) {
            return validity;
        }

        return nftSupplyKeyCheck(op.getTokenType(), op.hasSupplyKey());
    }

    private ResponseCodeEnum validateAutoRenewAccount(
            final TokenCreateTransactionBody op, final OptionValidator validator) {
        ResponseCodeEnum validity = OK;
        if (op.hasAutoRenewAccount()) {
            validity = validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod()) ? OK : INVALID_RENEWAL_PERIOD;
            return validity;
        }
        return validity;
    }
}
