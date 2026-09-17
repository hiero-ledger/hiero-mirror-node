// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class ActionTraceRequest extends ContractCallRequest {

    @JsonProperty("block_override")
    @Nullable
    @Valid
    private BlockOverride blockOverride;

    @JsonProperty("only_top_call")
    private boolean onlyTopCall;

    @Nullable
    private String timeout;
}
