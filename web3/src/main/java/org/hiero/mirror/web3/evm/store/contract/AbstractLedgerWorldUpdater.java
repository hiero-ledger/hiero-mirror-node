// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import java.util.Collection;
import org.hiero.mirror.web3.evm.store.Store;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

public abstract class AbstractLedgerWorldUpdater<W extends WorldView, A extends Account>
        extends AbstractLedgerEvmWorldUpdater<W, A> {

    protected final Store store;

    protected AbstractLedgerWorldUpdater(W world, AccountAccessor accountAccessor, Store store) {
        super(world, accountAccessor);
        this.store = store;
    }

    protected AbstractLedgerWorldUpdater(
            W world,
            AccountAccessor accountAccessor,
            TokenAccessor tokenAccessor,
            HederaEvmEntityAccess hederaEvmEntityAccess,
            Store store) {
        super(world, accountAccessor, tokenAccessor, hederaEvmEntityAccess);
        this.store = store;
    }

    @Override
    public void deleteAccount(Address address) {
        store.deleteAccount(address);
        deletedAccounts.add(address);
        updatedAccounts.remove(address);
    }

    protected Collection<Address> getDeletedAccounts() {
        return deletedAccounts;
    }
}
