// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * This class is a modified copy of AllowancePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Added util method to unalias given address
 */
public abstract class AbstractWritePrecompile implements Precompile {
    protected static final String FAILURE_MESSAGE = "Invalid full prefix for %s precompile!";
    protected final PrecompilePricingUtils pricingUtils;
    protected final SyntheticTxnFactory syntheticTxnFactory;

    protected AbstractWritePrecompile(
            final PrecompilePricingUtils pricingUtils, final SyntheticTxnFactory syntheticTxnFactory) {
        this.pricingUtils = pricingUtils;
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    @Override
    public long getGasRequirement(
            long blockTimestamp, final TransactionBody.Builder transactionBody, final AccountID sender) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody, sender);
    }

    protected Address unalias(Address addressOrAlias, HederaEvmStackedWorldStateUpdater updater) {
        return Address.wrap(Bytes.wrap(updater.permissivelyUnaliased(addressOrAlias.toArray())));
    }
}
