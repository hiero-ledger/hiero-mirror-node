// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.account;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class AccountAccessorImpl implements AccountAccessor {
    public static final int EVM_ADDRESS_SIZE = 20;

    private final Store store;
    private final HederaEvmEntityAccess mirrorEntityAccess;
    private final MirrorEvmContractAliases aliases;

    @Override
    public Address canonicalAddress(Address addressOrAlias) {
        if (aliases.isInUse(addressOrAlias)) {
            return addressOrAlias;
        }

        return getAddressOrAlias(addressOrAlias);
    }

    @Override
    public boolean isTokenAddress(Address address) {
        return mirrorEntityAccess.isTokenAccount(address);
    }

    public Address getAddressOrAlias(final Address address) {
        if (mirrorEntityAccess.isExtant(address)) {
            return address;
        }
        // An EIP-1014 address is always canonical
        if (!aliases.isMirror(address)) {
            return address;
        }

        final var account = store.getAccount(address, OnMissing.DONT_THROW);
        return account.canonicalAddress();
    }
}
