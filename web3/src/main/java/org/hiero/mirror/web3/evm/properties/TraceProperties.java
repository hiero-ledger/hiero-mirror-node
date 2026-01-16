// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.web3.evm.trace")
public class TraceProperties {

    private boolean enabled = false;

    @NonNull
    private Set<@Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH) String> contract = new HashSet<>();

    @NonNull
    private Set<State> status = new HashSet<>();

    public boolean stateFilterCheck(State state) {
        return !getStatus().isEmpty() && !getStatus().contains(state);
    }

    public boolean contractFilterCheck(String contract) {
        return !getContract().isEmpty() && !getContract().contains(contract);
    }
}
