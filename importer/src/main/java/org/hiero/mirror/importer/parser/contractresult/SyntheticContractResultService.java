// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractresult;

/**
 * This service is used to centralize the conversion logic from record stream
 * items to separate
 * synthetic contract results for HAPI token transactions
 */
public interface SyntheticContractResultService {
    void create(SyntheticContractResult result);
}
