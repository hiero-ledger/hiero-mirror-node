// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.account;

import static com.hedera.services.utils.MiscUtils.isRecoveredEvmAddress;

import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JECDSASecp256k1Key;
import com.hedera.services.jproto.JKey;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
@Named
public class MirrorEvmContractAliases extends HederaEvmContractAliases {

    final Store store;

    public boolean maybeLinkEvmAddress(@Nullable final JKey key, final Address address) {
        final var evmAddress = tryAddressRecovery(key);
        if (isRecoveredEvmAddress(evmAddress)) {
            if (evmAddress != null) {
                store.linkAlias(Address.wrap(Bytes.wrap(evmAddress)), address);
            }
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private byte[] tryAddressRecovery(@Nullable final JKey key) {
        if (key != null) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.getECDSASecp256k1Key();
            if (keyBytes.length == JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                final var evmAddress = EthSigsUtils.recoverAddressFromPubKey(keyBytes);
                if (isRecoveredEvmAddress(evmAddress)) {
                    return evmAddress;
                }
            }
        }
        return null;
    }

    @Override
    public Address resolveForEvm(Address addressOrAlias) {
        if (isMirror(addressOrAlias)) {
            return addressOrAlias;
        }

        final var account = store.getAccount(addressOrAlias, OnMissing.DONT_THROW);

        if (account.isEmptyAccount()) {
            return Address.ZERO;
        } else {
            return account.getId().asEvmAddress();
        }
    }

    public boolean isNativePrecompileAddress(Address addressOrAlias) {
        return addressOrAlias != null
                && addressOrAlias.compareTo(Address.ZERO) > 0
                && addressOrAlias.compareTo(Address.KZG_POINT_EVAL) < 0;
    }

    public boolean isInUse(final Address address) {
        return store.exists(address);
    }

    public void link(final Address alias, final Address address) {
        store.linkAlias(alias, address);
    }
}
