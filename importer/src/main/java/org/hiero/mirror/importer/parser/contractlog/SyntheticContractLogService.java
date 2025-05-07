// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

/**
 * This service is used to centralize the conversion logic from record stream
 * items to separate
 * synthetic events for HAPI token transactions
 */
public interface SyntheticContractLogService {
    void create(SyntheticContractLog log);
}
