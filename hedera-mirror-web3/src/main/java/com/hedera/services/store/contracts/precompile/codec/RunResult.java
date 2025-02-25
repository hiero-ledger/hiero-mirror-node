// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

/**
 * Marker interface for records that would contain results from the execution of the precompile run methods. We need to save the result in a record, so that
 *  we achieve stateless behaviour of the precompiles.
 *  */
public interface RunResult {}
