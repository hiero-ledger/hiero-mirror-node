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
     * Maximum total number of 32-byte EVM memory words captured across all opcodes of a single trace. Each captured
     * word is retained as a hex String (~117 bytes/word on JDK 25), so the default of 750,000 bounds retained heap to
     * ~87.6 MB rather than the raw 32-byte word size. Once the running total reaches this limit the trace is
     * truncated and the remaining opcodes are dropped.
     */
    @Positive
    private int maxMemoryWords = 750_000;

    /**
     * Maximum total number of stack items captured across all opcodes of a single trace, at the same ~117 bytes/item
     * heap cost as {@link #maxMemoryWords}. The default of 250,000 (~29 MB) keeps the original 1:3 ratio to
     * {@link #maxMemoryWords}. Once the running total reaches this limit the trace is truncated and the remaining
     * opcodes are dropped.
     */
    @Positive
    private int maxStack = 250_000;

    /**
     * Maximum total number of storage entries captured across all opcodes of a single trace. Storage capture reflects
     * the cumulative transaction storage, which grows with every touched slot; capping the total across opcodes bounds
     * the whole response (and the otherwise O(n^2) heap cost) for storage-heavy transactions. Once reached the trace is
     * truncated and the remaining opcodes are dropped.
     */
    @Positive
    private int maxStorage = 100_000;

    /**
     * Maximum number of opcode trace requests allowed to execute concurrently, including while a slow client is
     * still downloading the response. Independent of {@code throttle.opcodeRequestsPerSecond}, which only limits
     * how fast requests are admitted, not how many stay in flight at once.
     */
    @Positive
    private int maxConcurrentTraces = 3;
}
