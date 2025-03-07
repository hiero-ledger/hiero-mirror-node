// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Logic type from hedera-services. Differences with the original:
 *  1. Removed validations performed in UsageLimits, since they check global node limits,
 *  while on Archive Node we are interested in transaction scope only
 *  2. Use abstraction for the state by introducing {@link Store} interface
 *  3. Use Mirror Node specific properties - {@link MirrorNodeEvmProperties}
 *  4. Use copied models from hedera-services which are enhanced with additional constructors for easier setup,
 *  those are {@link Account}, {@link Token}, {@link TokenRelationship}
 * */
public class AssociateLogic {
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public AssociateLogic(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public void associate(final Address accountAddress, final List<Address> tokensAddresses, final Store store) {
        /* Load the models */
        final var account = store.getAccount(accountAddress, OnMissing.THROW);
        final var tokens = tokensAddresses.stream()
                .map(t -> store.getToken(t, OnMissing.THROW))
                .toList();

        /* Associate and commit the changes */
        final var newTokenRelationships = associateWith(account, tokens, store, false);

        newTokenRelationships.forEach(store::updateTokenRelationship);
    }

    public List<TokenRelationship> associateWith(
            final Account account,
            final List<Token> tokens,
            final Store store,
            final boolean shouldEnableRelationship) {
        int numAssociations = account.getNumAssociations();
        final var proposedTotalAssociations = tokens.size() + numAssociations;

        validateFalse(exceedsTokenAssociationLimit(proposedTotalAssociations), TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

        final var updatedAccount = account.setNumAssociations(proposedTotalAssociations);

        final List<TokenRelationship> newModelRels = new ArrayList<>();
        for (final var token : tokens) {
            TokenRelationshipKey tokenRelationshipKey =
                    new TokenRelationshipKey(token.getId().asEvmAddress(), account.getAccountAddress());

            validateFalse(store.hasAssociation(tokenRelationshipKey), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
            final var newRel = shouldEnableRelationship
                    ? token.newEnabledRelationship(updatedAccount)
                    : token.newRelationshipWith(updatedAccount, false);
            numAssociations++;
            newModelRels.add(newRel);
        }

        store.updateAccount(updatedAccount);

        return newModelRels;
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        final var op = txn.getTokenAssociate();
        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }
        if (repeatsItself(op.getTokensList())) {
            return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
        }

        return OK;
    }

    private boolean exceedsTokenAssociationLimit(final int totalAssociations) {
        return totalAssociations > mirrorNodeEvmProperties.getMaxTokensPerAccount();
    }
}
