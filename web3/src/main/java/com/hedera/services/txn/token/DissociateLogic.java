// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Use copied models from hedera-services which are enhanced with additional constructors and/or lombok generated builder for easier setup,
 *  those are {@link Account}, {@link Token}, {@link TokenRelationship}
 *  3. Moved dissociateUsing method from Account to DissociateLogic
 *  4. Moved methods from com.hedera.app.service.mono.txns.token.process.Dissociation
 *  to DissociateLogic
 * */
public class DissociateLogic {

    public void dissociate(final Address address, final List<Address> tokenAddresses, final Store store) {

        final var account = store.getAccount(address, OnMissing.THROW);
        final var tokens = tokenAddresses.stream()
                .map(t -> store.getToken(t, OnMissing.THROW))
                .toList();

        dissociateUsing(account, tokens, store);
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        final TokenDissociateTransactionBody op = txn.getTokenDissociate();
        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }
        if (repeatsItself(op.getTokensList())) {
            return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
        }
        return OK;
    }

    /**
     * Based on the dissociateUsing method in com.hedera.node.app.service.mono.store.models.Account
     * in hedera-services. Instead of the List of Dissociation and validator we provide the Account, a List of tokens
     * to be dissociated and the Store.
     * We first check if the account and provided tokens have association with the hasAssociation method,
     * then we call the updateRelationship method that is based on the updateModelRelsSubjectTo in the hedera-services
     * but adapted to work with the Store logic.
     * The inside calls to updateRelationshipForDeletedOrRemovedToken and updateModelsForDissociationFromActiveToken
     * keep the same behaviour but again adapted to work with the Store.
     * For validating the token expiration we introduced the validateTokenExpiration method
     * instead of the OptionValidator in hedera-services.
     * The association counters are also kept and adapted with the Store logic.
     * Finally, we publish the updated entities to the Store.
     * @param account
     * @param tokens
     * @param store
     */
    private void dissociateUsing(final Account account, final List<Token> tokens, final Store store) {

        tokens.forEach(token -> {
            final var tokenRelationshipKey =
                    new TokenRelationshipKey(token.getId().asEvmAddress(), account.getAccountAddress());
            validateTrue(store.hasAssociation(tokenRelationshipKey), TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
            var tokenRelationship = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);
            final var updatedRelationship = updateRelationship(tokenRelationship, store);
            Account updatedAccount = null;
            final var oldAccount = updatedRelationship.getAccount();
            if (updatedRelationship.isAutomaticAssociation()) {
                updatedAccount = oldAccount.decrementUsedAutomaticAssociations();
            }
            if (updatedRelationship.getBalanceChange() != 0) {
                var newBalance = oldAccount.getNumPositiveBalances();
                updatedAccount = oldAccount.setNumPositiveBalances(--newBalance);
            }
            if (updatedAccount != null) {
                store.updateAccount(updatedAccount);
            }

            if (updatedRelationship.isDestroyed()) {
                store.deleteTokenRelationship(updatedRelationship);
            }
        });

        var numAssociations = account.getNumAssociations();
        final var updatedAccount = account.setNumAssociations(numAssociations - tokens.size());

        store.updateAccount(updatedAccount);
    }

    private TokenRelationship updateRelationship(TokenRelationship tokenRelationship, final Store store) {

        final var token = tokenRelationship.getToken();
        if (token.isDeleted() || token.isBelievedToHaveBeenAutoRemoved()) {
            tokenRelationship = updateRelationshipForDeletedOrRemovedToken(tokenRelationship, store);
        } else {
            tokenRelationship = updateModelsForDissociationFromActiveToken(tokenRelationship, store);
        }

        return tokenRelationship.markAsDestroyed();
    }

    private TokenRelationship updateRelationshipForDeletedOrRemovedToken(
            TokenRelationship tokenRelationship, final Store store) {
        final var disappearingUnits = tokenRelationship.getBalance();
        tokenRelationship = tokenRelationship.setBalance(0L);
        final var token = tokenRelationship.getToken();
        if (token.getType() == NON_FUNGIBLE_UNIQUE) {
            final var account = tokenRelationship.getAccount();
            final var currentOwnedNfts = account.getOwnedNfts();
            final var updatedAccount = account.setOwnedNfts(currentOwnedNfts - disappearingUnits);
            store.updateAccount(updatedAccount);
            tokenRelationship = tokenRelationship.setAccount(updatedAccount);
        }
        return tokenRelationship;
    }

    private TokenRelationship updateModelsForDissociationFromActiveToken(
            TokenRelationship tokenRelationship, final Store store) {
        final var token = tokenRelationship.getToken();
        final var isAccountTreasuryOfDissociatedToken = tokenRelationship
                .getAccount()
                .getId()
                .equals(token.getTreasury().getId());
        validateFalse(isAccountTreasuryOfDissociatedToken, ACCOUNT_IS_TREASURY);
        validateFalse(tokenRelationship.isFrozen(), ACCOUNT_FROZEN_FOR_TOKEN);

        final var balance = tokenRelationship.getBalance();
        if (balance > 0L) {
            validateFalse(token.getType() == NON_FUNGIBLE_UNIQUE, ACCOUNT_STILL_OWNS_NFTS);

            validateTokenExpiration(token);

            /* If the fungible common token is expired, we automatically transfer the
            dissociating account's balance back to the treasury. */
            tokenRelationship = tokenRelationship.setBalance(0L);
            final var treasury = token.getTreasury();
            final var newTreasuryBalance = treasury.getBalance() + balance;
            final var updatedTreasury = treasury.setBalance(newTreasuryBalance);
            store.updateAccount(updatedTreasury);
        }
        return tokenRelationship;
    }

    private void validateTokenExpiration(Token token) {
        final var isTokenExpired = Instant.now().getEpochSecond() > token.getExpiry();
        validateTrue(isTokenExpired, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
    }
}
