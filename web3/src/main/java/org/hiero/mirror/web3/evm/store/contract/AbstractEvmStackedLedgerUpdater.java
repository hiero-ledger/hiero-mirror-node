// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import org.hiero.mirror.web3.evm.store.Store;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

public abstract class AbstractEvmStackedLedgerUpdater<W extends WorldView, A extends Account>
        extends AbstractLedgerWorldUpdater<AbstractLedgerWorldUpdater<W, A>, UpdateTrackingAccount<A>> {

    protected AbstractEvmStackedLedgerUpdater(
            final AbstractLedgerWorldUpdater<W, A> world,
            final AccountAccessor accountAccessor,
            final TokenAccessor tokenAccessor,
            final HederaEvmEntityAccess entityAccess,
            final Store store) {
        super(world, accountAccessor, tokenAccessor, entityAccess, store);
    }

    @Override
    public UpdateTrackingAccount<A> getForMutation(Address address) {
        final var wrapped = wrappedWorldView();
        final A account = wrapped.getForMutation(address);
        return account == null ? null : new UpdateTrackingAccount<>(account, null);
    }

    @Override
    public void commit() {
        // partially copied from services
        final var wrapped = wrappedWorldView();
        getDeletedAccounts().forEach(wrapped.getUpdatedAccounts()::remove);
        wrapped.getDeletedAccounts().addAll(getDeletedAccounts());
        for (final var updatedAccount : getUpdatedAccounts().values()) {
            var mutable = wrapped.getUpdatedAccounts().get(updatedAccount.getAddress());
            if (mutable == null) {
                mutable = updatedAccount.getWrappedAccount();
                if (mutable == null) {
                    mutable = new UpdateTrackingAccount<>(updatedAccount.getAddress(), null);
                }
                wrapped.getUpdatedAccounts().put(mutable.getAddress(), mutable);
            }
            mutable.setNonce(updatedAccount.getNonce());
            if (!updatedAccount.wrappedAccountIsTokenProxy()) {
                mutable.setBalance(updatedAccount.getBalance());
            }
            if (updatedAccount.codeWasUpdated()) {
                mutable.setCode(updatedAccount.getCode());
            }
            if (updatedAccount.getStorageWasCleared()) {
                mutable.clearStorage();
            }
            updatedAccount.getUpdatedStorage().forEach(mutable::setStorageValue);
        }
        store.commit();
    }
}
