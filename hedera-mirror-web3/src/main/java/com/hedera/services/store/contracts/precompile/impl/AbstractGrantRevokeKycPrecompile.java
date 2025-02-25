// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

/**
 * This class is a modified copy of AbstractGrantRevokeKycPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Run method is modified to return {@link RunResult}, so that getSuccessResultFor is based on this record
 *  4. Run method is modified to accept {@link Store} as a parameter, so that we abstract from the internal state that is used for the execution
 */
public abstract class AbstractGrantRevokeKycPrecompile extends AbstractWritePrecompile {

    protected AbstractGrantRevokeKycPrecompile(
            SyntheticTxnFactory syntheticTxnFactory, PrecompilePricingUtils pricingUtils) {
        super(pricingUtils, syntheticTxnFactory);
    }
}
