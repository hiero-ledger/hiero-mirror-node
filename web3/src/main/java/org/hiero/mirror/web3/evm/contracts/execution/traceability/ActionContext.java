// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.viewmodel.TracerConfig;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ActionContext {

    @Builder.Default
    private List<ActionResponse> actions = List.of();

    private long gasRemaining;

    private TracerConfig tracerConfig;

    public void addAction(final ActionResponse actionResponse) {
        actions.add(actionResponse);
    }
}
