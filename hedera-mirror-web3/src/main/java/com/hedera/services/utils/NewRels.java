// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txn.token.AssociateLogic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NewRels {
    public static List<TokenRelationship> listFrom(
            final Token provisionalToken, final Store store, final AssociateLogic associateLogic) {
        final var treasury = provisionalToken.getTreasury();
        final Set<Id> associatedSoFar = new HashSet<>();
        final List<TokenRelationship> newRels = new ArrayList<>();

        associateGiven(provisionalToken, treasury, store, associatedSoFar, newRels, associateLogic);
        for (final var customFee : provisionalToken.getCustomFees()) {
            final var collector = CustomFeeUtils.getFeeCollector(customFee);
            final var account = store.getAccount(collector, OnMissing.THROW);
            associateGiven(provisionalToken, account, store, associatedSoFar, newRels, associateLogic);
        }

        return newRels;
    }

    private static void associateGiven(
            final Token provisionalToken,
            final Account account,
            final Store store,
            final Set<Id> associatedSoFar,
            final List<TokenRelationship> newRelations,
            final AssociateLogic associateLogic) {
        final var accountId = account.getId();
        if (associatedSoFar.contains(accountId)) {
            return;
        }
        final var newRelationship = associateLogic
                .associateWith(account, List.of(provisionalToken), store, true)
                .get(0);
        newRelations.add(newRelationship);
        associatedSoFar.add(accountId);
    }

    private NewRels() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
