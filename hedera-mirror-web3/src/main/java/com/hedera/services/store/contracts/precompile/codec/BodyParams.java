// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

/**
 * Marker interface for records that would contain additional arguments for the Precompile.body method. This is needed,
 * so that we can achieve stateless behaviour and pass the additional needed information.
 */
public interface BodyParams {}
