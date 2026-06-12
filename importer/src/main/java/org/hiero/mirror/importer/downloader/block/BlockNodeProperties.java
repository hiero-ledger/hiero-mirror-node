// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.google.common.collect.ImmutableSortedSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.SortedSet;
import lombok.Data;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public final class BlockNodeProperties implements Comparable<BlockNodeProperties> {

    public static final SortedSet<BlockNodeApi> FULL_BLOCK_NODE_APIS =
            ImmutableSortedSet.of(BlockNodeApi.STATUS, BlockNodeApi.SUBSCRIBE_STREAM);

    private static final Comparator<BlockNodeProperties> COMPARATOR = Comparator.comparing(
                    BlockNodeProperties::getPriority)
            .thenComparing(BlockNodeProperties::getEndpoints, sortedSetComparator());

    @NotEmpty
    private SortedSet<@Valid ServiceEndpoint> endpoints;

    @Min(0)
    private int priority = 0;

    @Override
    public int compareTo(final BlockNodeProperties other) {
        return COMPARATOR.compare(this, other);
    }

    private static <T extends Comparable<T>> Comparator<SortedSet<T>> sortedSetComparator() {
        return (a, b) -> {
            var iterA = a.iterator();
            var iterB = b.iterator();
            while (iterA.hasNext() && iterB.hasNext()) {
                int c = iterA.next().compareTo(iterB.next());
                if (c != 0) return c;
            }
            return Integer.compare(a.size(), b.size());
        };
    }

    @Data
    @Validated
    public static final class ServiceEndpoint implements Comparable<ServiceEndpoint> {

        private static final Comparator<ServiceEndpoint> COMPARATOR = Comparator.comparing(ServiceEndpoint::getHost)
                .thenComparingInt(ServiceEndpoint::getPort)
                .thenComparing(ServiceEndpoint::getApis, BlockNodeProperties.sortedSetComparator())
                .thenComparing(ServiceEndpoint::isRequiresTls);

        @NotEmpty
        private SortedSet<@NotNull BlockNodeApi> apis = FULL_BLOCK_NODE_APIS;

        @NotBlank
        private String host;

        @Max(65535)
        @Min(0)
        private int port = 40840;

        private boolean requiresTls;

        @Override
        public int compareTo(final ServiceEndpoint other) {
            return COMPARATOR.compare(this, other);
        }

        @Override
        public String toString() {
            return "%s:%d".formatted(host, port);
        }
    }
}
