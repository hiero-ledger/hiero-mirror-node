// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hiero.mirror.web3.opcode.tracer")
@Data
@Validated
public class OpcodesProperties {
    private boolean enabled = true;

    /**
     * Maximum number of opcodes recorded per trace request. Once reached the trace is truncated with a single marker
     * opcode and the remaining opcodes are dropped. This bounds the base opcode list (pc/op/gas/…) independently of the
     * memory/stack/storage budgets below, which is the only thing limiting a trace when those captures are disabled.
     */
    @Positive
    private int maxOpcodes = 20_000;

    /**
     * Maximum total number of 32-byte EVM memory words captured across all opcodes of a single trace (default
     * 3,000,000 ≈ 96 MB). Once the running total reaches this limit the trace is truncated and the remaining opcodes are
     * dropped. Unlike a per-opcode cap this bounds the whole response, so a single large opcode is captured in full as
     * long as the trace total stays within budget.
     */
    @Positive
    private int maxMemoryWords = 3_000_000;

    /**
     * Maximum total number of stack items captured across all opcodes of a single trace. Once the running total reaches
     * this limit the trace is truncated and the remaining opcodes are dropped.
     */
    @Positive
    private int maxStack = 1_000_000;

    /**
     * Maximum total number of storage entries captured across all opcodes of a single trace. Storage capture reflects
     * the cumulative transaction storage, which grows with every touched slot; capping the total across opcodes bounds
     * the whole response (and the otherwise O(n^2) heap cost) for storage-heavy transactions. Once reached the trace is
     * truncated and the remaining opcodes are dropped.
     */
    @Positive
    private int maxStorage = 100_000;
}
