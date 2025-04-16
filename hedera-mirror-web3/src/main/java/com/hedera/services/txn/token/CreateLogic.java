// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.models.Token.fromGrpcOpAndMeta;
import static com.hedera.services.utils.CustomFeeUtils.getFeeCollector;
import static com.hedera.services.utils.CustomFeeUtils.getFeeType;
import static com.hedera.services.utils.NewRels.listFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Logic type from hedera-services. Differences with the original: 1. Use abstraction for the state by
 * introducing {@link Store} interface. 2. Use copied models from hedera-services which are enhanced with additional
 * constructors and/or lombok generated builder for easier setup, those are {@link Account}, {@link Token},
 * {@link TokenRelationship}, {@link CustomFee}. 3. Moved methods from
 * com.hedera.app.service.mono.txns.token.process.Creation to {@link CreateLogic}. 4. Provided
 * {@link com.hedera.services.utils.CustomFeeUtils} for easier work with fees. 5. Adapted validation methods with the
 * logic in {@link CustomFee}.
 */
public class CreateLogic {

    private final MirrorNodeEvmProperties dynamicProperties;
    private final AssociateLogic associateLogic;

    public CreateLogic(final MirrorNodeEvmProperties dynamicProperties, final AssociateLogic associateLogic) {
        this.dynamicProperties = dynamicProperties;
        this.associateLogic = associateLogic;
    }

    public void create(
            final long now,
            final Address activePayer,
            final OptionValidator validator,
            Store store,
            final TokenCreateTransactionBody op) {

        final var treasuryId = Id.fromGrpcAccount(op.getTreasury()).asEvmAddress();
        final var treasury = store.getAccount(treasuryId, OnMissing.THROW);
        Account autoRenew = null;
        Id provisionalId = Id.fromGrpcToken(EntityIdUtils.tokenIdFromEvmAddress(activePayer));

        if (op.hasAutoRenewAccount()) {
            final var autoRenewId = Id.fromGrpcAccount(op.getAutoRenewAccount()).asEvmAddress();
            autoRenew = store.getAccount(autoRenewId, OnMissing.THROW);
        }
        // --- Create the model objects ---
        validateExpiration(validator, op);

        // --- Do the business logic ---
        final var token = doProvisionallyWith(now, store, op, treasury, autoRenew, provisionalId);
        var newRels = getNewRelationships(token, store, op);

        // --- Persist the created model ---
        persist(store, token, newRels);
    }

    public enum FeeType {
        FRACTIONAL_FEE,
        FIXED_FEE,
        ROYALTY_FEE
    }

    private void validateExpiration(final OptionValidator validator, final TokenCreateTransactionBody op) {
        final var hasValidOrNoExplicitExpiry = !op.hasExpiry() || validator.isValidExpiry(op.getExpiry());
        validateTrue(hasValidOrNoExplicitExpiry, INVALID_EXPIRATION_TIME);
    }

    private void persist(final Store store, final Token provisionalToken, final List<TokenRelationship> newRels) {
        store.updateToken(provisionalToken);
        newRels.forEach(rel -> updateRelationshipAndAccount(rel, store));
    }

    private Token doProvisionallyWith(
            final long now,
            final Store store,
            final TokenCreateTransactionBody op,
            final Account treasury,
            final Account autoRenew,
            final Id provisionalId) {
        final var maxCustomFees = dynamicProperties.maxCustomFeesAllowed();
        validateTrue(op.getCustomFeesCount() <= maxCustomFees, CUSTOM_FEES_LIST_TOO_LONG);

        final var provisionalToken = fromGrpcOpAndMeta(provisionalId, op, treasury, autoRenew, now);
        provisionalToken.getCustomFees().forEach(fee -> validateAndFinalizeWith(provisionalToken, fee, store));

        return provisionalToken;
    }

    private List<TokenRelationship> getNewRelationships(
            final Token provisionalToken, final Store store, final TokenCreateTransactionBody op) {
        final var newRels = listFrom(provisionalToken, store, associateLogic);
        if (op.getInitialSupply() > 0) {
            // Treasury relationship is always first
            provisionalToken.mint(newRels.get(0), op.getInitialSupply(), true);
        }
        return newRels;
    }

    private void validateAndFinalizeWith(final Token provisionalToken, final CustomFee customFee, final Store store) {
        validate(provisionalToken, customFee, store);
    }

    private void updateRelationshipAndAccount(final TokenRelationship relationship, final Store store) {
        store.updateTokenRelationship(relationship);
        store.updateAccount(relationship.getAccount());
    }

    private void validate(final Token token, final CustomFee customFee, final Store store) {
        final var feeType = getFeeType(customFee);
        final var collector = store.getAccount(getFeeCollector(customFee), OnMissing.THROW);

        switch (feeType) {
            case FIXED_FEE -> validateAndFinalizeFixedFeeWith(token, collector, store, customFee);
            case ROYALTY_FEE -> {
                validateTrue(token.isNonFungibleUnique(), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                validateAndFinalizeRoyaltyFeeWith(token, collector, store, customFee);
            }
            case FRACTIONAL_FEE ->
                validateTrue(token.isFungibleCommon(), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
        }
    }

    private void validateAndFinalizeFixedFeeWith(
            final Token provisionalToken, final Account feeCollector, final Store store, final CustomFee customFee) {
        final var feeType = getFeeType(customFee);
        var denominatingTokenId = feeType.equals(FeeType.FIXED_FEE)
                ? customFee.getFixedFee().getDenominatingTokenId()
                : customFee.getRoyaltyFee().getDenominatingTokenId();
        var useHbarForPayment = feeType.equals(FeeType.FIXED_FEE)
                ? customFee.getFixedFee().isUseHbarsForPayment()
                : customFee.getRoyaltyFee().isUseHbarsForPayment();
        if (denominatingTokenId.isZero() && !useHbarForPayment) {
            validateTrue(provisionalToken.isFungibleCommon(), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
            denominatingTokenId = provisionalToken.getId().asEvmAddress();
            final var newFixedFee = setNewDenominationTokenId(customFee.getFixedFee(), denominatingTokenId);
            customFee.setFixedFee(newFixedFee);
        } else {
            validateExplicitlyDenominatedWith(feeCollector, store, denominatingTokenId);
        }
    }

    private void validateAndFinalizeRoyaltyFeeWith(
            final Token token, final Account collector, final Store store, final CustomFee customFee) {
        validateTrue(token.isNonFungibleUnique(), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
        final var royalty = customFee.getRoyaltyFee();
        if ((!royalty.getDenominatingTokenId().isZero() || royalty.isUseHbarsForPayment())
                && royalty.getAmount() > 0L) {
            validateAndFinalizeFixedFeeWith(token, collector, store, customFee);
        }
    }

    private FixedFee setNewDenominationTokenId(final FixedFee fixedFee, final Address denominatingTokenId) {
        return new FixedFee(
                fixedFee.getAmount(),
                denominatingTokenId,
                fixedFee.isUseHbarsForPayment(),
                fixedFee.isUseCurrentTokenForPayment(),
                fixedFee.getFeeCollector());
    }

    private void validateExplicitlyDenominatedWith(
            final Account feeCollector, final Store store, final Address denomId) {
        final var denomToken = store.getToken(denomId, OnMissing.THROW);
        validateTrue(denomToken.isFungibleCommon(), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
        validateTrue(
                store.hasAssociation(
                        new TokenRelationshipKey(denomToken.getId().asEvmAddress(), feeCollector.getAccountAddress())),
                TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }
}
