// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.jspecify.annotations.NonNull;

public interface OpcodeService {

    /**
     * @param opcodeRequest the {@link OpcodeRequest}
     * @return the {@link OpcodesResponse} holding the result of the opcode call
     */
    OpcodesResponse processOpcodeCall(@NonNull OpcodeRequest opcodeRequest);
}
