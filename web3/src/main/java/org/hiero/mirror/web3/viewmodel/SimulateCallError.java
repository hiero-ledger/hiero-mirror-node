// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonInclude;

// Serialized as an empty object for a successful call.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulateCallError(Integer code, String data, String message) {

    public static final SimulateCallError NONE = new SimulateCallError(null, null, null);
}
