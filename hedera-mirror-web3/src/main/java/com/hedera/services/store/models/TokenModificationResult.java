// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

/**
 * Newly introduced record to allow multiple return types from {@link Token} modification operations}
 *
 * Once we start consuming more libraries from hedera-services, we may delete this record.
 */
public record TokenModificationResult(Token token, TokenRelationship tokenRelationship) {}
