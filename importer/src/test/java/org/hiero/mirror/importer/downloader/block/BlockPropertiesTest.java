// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleEndpointProperties;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleServiceEndpoint;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableSortedSet;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.unit.DataSize;

final class BlockPropertiesTest {

    @AutoClose
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    @Test
    void initPropagatesMaxBlockSizeToStreamFileData() throws IOException {
        var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.getStream().setMaxBlockSize(DataSize.ofBytes(100));
        blockProperties.init();

        try {
            String filename = "2021-03-10T16_00_00Z.rcd.gz";
            byte[] uncompressedBytes = new byte[1000];

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (OutputStream os = new GZIPOutputStream(baos)) {
                    os.write(uncompressedBytes);
                }

                StreamFileData streamFileData = StreamFileData.from(filename, baos.toByteArray());

                assertThrows(InvalidStreamFileException.class, streamFileData::getDecompressedBytes);
            }
        } finally {
            new BlockProperties(new ImporterProperties()).init();
        }
    }

    @Test
    void springBoundMaxBlockSizePropagatesToStreamFileData() throws IOException {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "test", Map.of("hiero.mirror.importer.block.stream.maxBlockSize", "100B")));
        ConfigurationPropertiesBindingPostProcessor.register(context.getDefaultListableBeanFactory());
        context.register(ImporterProperties.class);
        context.register(BlockProperties.class);
        context.refresh();

        try {
            var blockProperties = context.getBean(BlockProperties.class);
            assertThat(blockProperties.getStream().getMaxBlockSize().toBytes()).isEqualTo(100);

            String filename = "2021-03-10T16_00_00Z.rcd.gz";
            byte[] uncompressedBytes = new byte[1000];

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (OutputStream os = new GZIPOutputStream(baos)) {
                    os.write(uncompressedBytes);
                }

                StreamFileData streamFileData = StreamFileData.from(filename, baos.toByteArray());

                assertThrows(InvalidStreamFileException.class, streamFileData::getDecompressedBytes);
            }
        } finally {
            context.close();
            new BlockProperties(new ImporterProperties()).init();
        }
    }

    @Test
    void getBucketName() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.getImporterProperties().setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        assertThat(blockProperties.getBucketName()).isEqualTo("hedera-testnet-recent-block-streams");

        blockProperties.setBucketName("hedera-testnet-alt-block-streams");
        assertThat(blockProperties.getBucketName()).isEqualTo("hedera-testnet-alt-block-streams");
    }

    @Test
    void hasValidEndpointsWhenNoNodes() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        assertThat(violationPaths(blockProperties)).doesNotContain("validEndpoints");
    }

    @Test
    void hasValidEndpointsWhenBothApisInSingleEndpoint() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setNodes(List.of(singleEndpointProperties("a")));
        assertThat(violationPaths(blockProperties)).doesNotContain("validEndpoints");
    }

    @Test
    void hasValidEndpointsWhenApisSplitAcrossEndpoints() {
        final var statusEndpoint = singleServiceEndpoint(BlockNodeApi.STATUS, "a", 40840);
        final var subscribeEndpoint = singleServiceEndpoint(BlockNodeApi.SUBSCRIBE_STREAM, "a", 40841);
        final var node = new BlockNodeProperties();
        node.setEndpoints(ImmutableSortedSet.of(statusEndpoint, subscribeEndpoint));
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setNodes(List.of(node));
        assertThat(violationPaths(blockProperties)).doesNotContain("validEndpoints");
    }

    @Test
    void hasValidEndpointsWhenApisSplitAcrossNodes() {
        final var node1 = new BlockNodeProperties();
        node1.setEndpoints(ImmutableSortedSet.of(singleServiceEndpoint(BlockNodeApi.STATUS, "a", 40840)));
        final var node2 = new BlockNodeProperties();
        node2.setEndpoints(ImmutableSortedSet.of(singleServiceEndpoint(BlockNodeApi.SUBSCRIBE_STREAM, "b", 40840)));
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setNodes(List.of(node1, node2));
        assertThat(violationPaths(blockProperties)).contains("validEndpoints");
    }

    @ParameterizedTest
    @EnumSource(
            names = {"STATUS", "SUBSCRIBE_STREAM"},
            value = BlockNodeApi.class)
    void hasValidEndpointsWhenOnlyOneApi(final BlockNodeApi provided) {
        final var node = new BlockNodeProperties();
        node.setEndpoints(ImmutableSortedSet.of(singleServiceEndpoint(provided, "a", 40840)));
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setNodes(List.of(node));
        assertThat(violationPaths(blockProperties)).contains("validEndpoints");
    }

    private static Set<String> violationPaths(BlockProperties properties) {
        return VALIDATOR_FACTORY.getValidator().validate(properties).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
