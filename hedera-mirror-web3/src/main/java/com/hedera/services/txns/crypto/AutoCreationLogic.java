// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto;

import static com.hedera.services.utils.EntityIdUtils.isAliasSizeGreaterThanEvmAddress;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Responsible for creating accounts during a crypto transfer that sends hbar to a previously unused alias.
 *
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Remove unused methods: reclaimPendingAliases, trackSigImpactIfNeeded, getPendingCreations, getTokenAliasMap
 * 3. The class is stateless and the arguments are passed into the functions
 */
public class AutoCreationLogic extends AbstractAutoCreationLogic {

    private final MirrorEvmContractAliases mirrorEvmContractAliases;

    public AutoCreationLogic(
            final FeeCalculator feeCalculator,
            final EvmProperties evmProperties,
            final SyntheticTxnFactory syntheticTxnFactory,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        super(feeCalculator, evmProperties, syntheticTxnFactory);
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
    }

    /**
     * Differences with the original:
     * Due to using {@link Address} in place of {@link ByteString} for the alias map key
     * we do additional check if the derived alias is more than 20 bytes and call
     * maybeLinkEvmAddress instead of link.
     *
     * @param alias
     * @param address
     */
    @Override
    protected void trackAlias(final ByteString alias, final Address address) {
        if (isAliasSizeGreaterThanEvmAddress(alias)) {
            // if the alias is not derived from ECDSA public key
            final var key = asPrimitiveKeyUnchecked(alias);
            JKey jKey = asFcKeyUnchecked(key);
            mirrorEvmContractAliases.maybeLinkEvmAddress(jKey, address);

        } else {
            // if the alias is derived from ECDSA public key
            mirrorEvmContractAliases.link(Address.wrap(Bytes.wrap(alias.toByteArray())), address);
        }
    }
}
