// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.jspecify.annotations.NonNull;

public interface PrestateService {

    /**
     * Uses the provided {@link PrestateRequest} to load account and contract state from the database for all entities
     * touched by the transaction sidecars.
     *
     * @param prestateRequest The {@link PrestateRequest} used to trace the transaction
     * @return {@link PrestateResponse} containing the list of account traces including pre and post execution state
     * changes
     */
    PrestateResponse processPrestateCall(@NonNull PrestateRequest prestateRequest);
}
