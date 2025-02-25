// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.contract;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class MockLedgerWorldUpdater extends AbstractLedgerWorldUpdater<HederaEvmMutableWorldState, Account> {

    public MockLedgerWorldUpdater(final HederaEvmWorldState world, final AccountAccessor accountAccessor) {
        super(world, accountAccessor, null);
    }

    @Override
    public Account getForMutation(Address address) {
        return null;
    }

    @Override
    public MutableAccount createAccount(Address address) {
        return super.createAccount(address);
    }

    @Override
    public MutableAccount getOrCreate(Address address) {
        return super.getOrCreate(address);
    }

    @Override
    public MutableAccount getOrCreateSenderAccount(Address address) {
        return super.getOrCreateSenderAccount(address);
    }

    @Override
    public MutableAccount getSenderAccount(MessageFrame frame) {
        return super.getSenderAccount(frame);
    }

    @Override
    public void commit() {
        // No op
    }
}
