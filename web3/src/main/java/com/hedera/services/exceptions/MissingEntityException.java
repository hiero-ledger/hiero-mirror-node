// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.exceptions;

import static com.hedera.services.utils.EntityIdUtils.readableId;

import java.io.Serial;

/**
 * Copied exception type from hedera-services.
 */
public class MissingEntityException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = -7729035252443821593L;

    public MissingEntityException(final Object id) {
        super(readableId(id));
    }
}
