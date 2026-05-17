// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

/**
 * Wrapper for contract execution result that contains the returned bytes (as hex string)
 * and the actual gas used by the execution.
 */
public record ContractExecutionResult(String result, long gasUsed) {}
