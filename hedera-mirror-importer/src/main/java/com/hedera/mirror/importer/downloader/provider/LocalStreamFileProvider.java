// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.provider;

import static com.hedera.mirror.common.domain.StreamType.SIGNATURE_SUFFIX;
import static com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType.NODE_ID;
import static java.util.Objects.requireNonNullElse;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.CustomLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
public class LocalStreamFileProvider extends AbstractStreamFileProvider {

    private static final File[] EMPTY = new File[0];

    private final LocalStreamFileProperties localProperties;

    public LocalStreamFileProvider(CommonDownloaderProperties properties, LocalStreamFileProperties localProperties) {
        super(properties);
        this.localProperties = localProperties;
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        var batchSize = properties.getBatchSize();
        var startAfter = lastFilename.getFilenameAfter();
        var stopwatch = Stopwatch.createStarted();
        var count = new AtomicLong(0L);

        return listFiles(properties.getPathType(), node, lastFilename)
                .switchIfEmpty(listFiles(NODE_ID, node, lastFilename))
                .timeout(properties.getTimeout())
                .sort()
                .take(batchSize)
                .map(this::toStreamFileData)
                .doOnNext(s -> count.incrementAndGet())
                .doOnComplete(() -> log.debug(
                        "Completed listing node {} for {} files after {} in {}", node, count, startAfter, stopwatch));
    }

    @Override
    protected Mono<StreamFileData> doGet(StreamFilename streamFilename) {
        var basePath = properties.getImporterProperties().getStreamPath().toFile();
        return Mono.fromSupplier(() -> new File(basePath, streamFilename.getFilePath()))
                .doOnNext(this::checkSize)
                .map(file -> StreamFileData.from(file, streamFilename))
                .timeout(properties.getTimeout())
                .onErrorMap(FileOperationException.class, TransientProviderException::new);
    }

    @Override
    protected String getBlockStreamFilePath(long shard, long nodeId, String filename) {
        return Path.of(String.valueOf(shard), String.valueOf(nodeId), filename).toString();
    }

    private Flux<File> listFiles(PathType pathType, ConsensusNode node, StreamFilename streamFilename) {
        var pathTypeProp = properties.getPathType();
        var streamType = streamFilename.getStreamType();

        // Once a node ID based file has been processed, optimize performance by disabling auto path lookup.
        if (pathTypeProp == PathType.AUTO && streamFilename.isNodeId()) {
            properties.setPathType(NODE_ID);
        } // Skip when we fall back to listing by node ID, but we're not on auto.
        else if (pathTypeProp != pathType && pathTypeProp != PathType.AUTO) {
            return Flux.empty();
        }

        return getBasePaths(streamFilename)
                .map(basePath -> {
                    var prefix =
                            switch (pathType) {
                                case ACCOUNT_ID, AUTO -> Path.of(
                                        streamType.getPath(), streamType.getNodePrefix() + node.getNodeAccountId());
                                case NODE_ID -> Path.of(
                                        properties.getImporterProperties().getNetwork(),
                                        String.valueOf(properties
                                                .getImporterProperties()
                                                .getShard()),
                                        String.valueOf(node.getNodeId()),
                                        streamType.getNodeIdBasedSuffix());
                            };

                    return basePath.resolve(prefix).toFile();
                })
                .doOnNext(f -> log.debug("Listing files for node {} in {}", node, f))
                .filter(File::exists)
                .flatMapSequential(dir -> Flux.fromArray(
                        requireNonNullElse(dir.listFiles(f -> matches(streamFilename.getFilenameAfter(), f)), EMPTY)));
    }

    /*
     * Search YYYY-MM-DD sub-folders if present, otherwise just search streams directory.
     */
    private Flux<Path> getBasePaths(StreamFilename streamFilename) {
        var basePath = properties.getImporterProperties().getStreamPath();
        var baseFile = basePath.toFile();
        baseFile.mkdirs();

        if (!baseFile.exists()) {
            return Flux.error(new RuntimeException("Unable to create directory: " + basePath));
        }

        try (var subDirs = Files.list(basePath)) {
            var date = LocalDate.ofInstant(streamFilename.getInstant(), ZoneOffset.UTC)
                    .toString();
            var paths = subDirs.map(Path::toFile)
                    .filter(f -> f.isDirectory()
                            && f.getName().compareTo(date) >= 0
                            && f.getName().length() == 10)
                    .sorted()
                    .limit(2) // Current and next day
                    .map(File::toPath)
                    .collect(Collectors.toSet());

            if (paths.isEmpty()) {
                return Flux.just(basePath);
            }

            return Flux.fromIterable(paths);
        } catch (Exception e) {
            return Flux.error(new RuntimeException(e));
        }
    }

    private void checkSize(File file) {
        long size = file.length();
        if (size > properties.getMaxSize()) {
            throw new InvalidDatasetException("Stream file " + file + " size " + size + " exceeds limit");
        }
    }

    private boolean matches(String lastFilename, File file) {
        if (!file.isFile() || !file.canRead() || file.length() > properties.getMaxSize()) {
            return false;
        }

        var name = file.getName();

        if (name.compareTo(lastFilename) < 0) {
            try {
                // Files before last file have been processed and can be deleted to optimize list + sort
                if (localProperties.isDeleteAfterProcessing()) {
                    Files.delete(file.toPath());
                }
            } catch (Exception e) {
                log.warn("Unable to delete file {}: {}", file, e.getMessage());
            }
            return false;
        }

        return name.contains(SIGNATURE_SUFFIX);
    }

    private StreamFileData toStreamFileData(File file) {
        var basePath = properties.getImporterProperties().getStreamPath();
        var filename = StreamFilename.from(basePath.relativize(file.toPath()).toString());
        return StreamFileData.from(basePath, filename);
    }
}
