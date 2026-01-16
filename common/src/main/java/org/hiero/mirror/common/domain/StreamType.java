// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import com.google.common.collect.ImmutableSortedSet;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;

@Getter
public enum StreamType {
    BALANCE(
            AccountBalanceFile::new,
            "accountBalances",
            "balance",
            "_Balances",
            List.of("csv", "pb"),
            Duration.ofMinutes(15L)),
    RECORD(RecordFile::new, "recordstreams", "record", "", List.of("rcd"), Duration.ofSeconds(2L)),
    BLOCK(BlockFile::new, "", "", "", List.of("blk"), Duration.ofMillis(500L));

    public static final String SIGNATURE_SUFFIX = "_sig";

    private final SortedSet<Extension> dataExtensions;
    private final String nodePrefix;
    private final String path;
    private final SortedSet<Extension> signatureExtensions;
    private final String suffix;
    private final String nodeIdBasedSuffix; // HIP-679
    private final Supplier<? extends StreamFile<?>> supplier;
    private final Duration fileCloseInterval;

    StreamType(
            Supplier<? extends StreamFile<?>> supplier,
            String path,
            String nodePrefix,
            String suffix,
            List<String> extensions,
            Duration fileCloseInterval) {
        this.supplier = supplier;
        this.path = path;
        this.nodePrefix = nodePrefix;
        this.suffix = suffix;
        this.nodeIdBasedSuffix = name().toLowerCase(Locale.ENGLISH);
        this.fileCloseInterval = fileCloseInterval;

        dataExtensions = IntStream.range(0, extensions.size())
                .mapToObj(index -> Extension.of(extensions.get(index), index))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
        signatureExtensions = dataExtensions.stream()
                .map(ext -> Extension.of(ext.getName() + SIGNATURE_SUFFIX, ext.getPriority()))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
    }

    public boolean isChained() {
        return this != BALANCE;
    }

    @SuppressWarnings("unchecked")
    public <T extends StreamFile<?>> T newStreamFile() {
        return (T) supplier.get();
    }

    @Value(staticConstructor = "of")
    public static class Extension implements Comparable<Extension> {
        private static final Comparator<Extension> COMPARATOR = Comparator.comparing(Extension::getPriority);

        String name;

        @EqualsAndHashCode.Exclude
        int priority; // starting from 0, larger value means higher priority

        @Override
        public int compareTo(Extension other) {
            return COMPARATOR.compare(this, other);
        }
    }
}
