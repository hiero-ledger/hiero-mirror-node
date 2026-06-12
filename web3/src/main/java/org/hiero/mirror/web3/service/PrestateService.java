// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.jspecify.annotations.NonNull;

public interface PrestateService {

    /**
     * A method that we will use the provided {@link PrestateRequest} to execute a transaction and trace the state for
     * all accounts that were touched during the execution.
     *
     * @param prestateRequest The {@link PrestateRequest} used to trace the transaction
     * @return {@link PrestateResponse} containing the list of account traces including pre and post execution state
     * changes
     * */
    PrestateResponse processPrestateCall(@NonNull PrestateRequest prestateRequest);
}
