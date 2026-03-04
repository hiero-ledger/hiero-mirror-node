// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import static org.hiero.mirror.common.domain.DigestAlgorithm.SHA_384;
import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.downloader.provider.TransientProviderException;
import org.hiero.mirror.importer.exception.FileOperationException;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.SignatureVerificationException;
import org.hiero.mirror.importer.reader.StreamFileReader;
import org.hiero.mirror.importer.reader.signature.SignatureFileReader;
import org.hiero.mirror.importer.util.ShutdownHelper;
import org.hiero.mirror.importer.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public abstract class Downloader<T extends StreamFile<I>, I extends StreamItem> {

    public static final String STREAM_CLOSE_LATENCY_METRIC_NAME = "hiero.mirror.importer.stream.close.latency";

    private static final String HASH_TYPE_FILE = "File";
    private static final String HASH_TYPE_METADATA = "Metadata";
    private static final String HASH_TYPE_RUNNING = "Running";

    private static final Comparator<StreamFileSignature> STREAM_FILE_SIGNATURE_COMPARATOR = (left, right) -> {
        if (Objects.equals(left, right)) {
            // Ensures values are unique when used in a Set
            return 0;
        }

        // The arbitrary ordering compares objects by identity, thus when used in a sorted collection, it gives a random
        // order of the StreamFileSignatures w.r.t the nodes
        return Ordering.arbitrary().compare(left, right);
    };

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final DownloaderProperties downloaderProperties;
    protected final ImporterProperties importerProperties;
    protected final NodeSignatureVerifier nodeSignatureVerifier;
    protected final SignatureFileReader signatureFileReader;
    protected final StreamFileProvider streamFileProvider;
    protected final StreamFileReader<T, ?> streamFileReader;
    protected final StreamFileNotifier streamFileNotifier;
    protected final DateRangeCalculator dateRangeCalculator;
    protected final AtomicReference<Optional<StreamFile<I>>> lastStreamFile = new AtomicReference<>(Optional.empty());

    private final ConsensusNodeService consensusNodeService;
    private final StreamType streamType;

    // Metrics
    private final MeterRegistry meterRegistry;
    private final Map<Long, Counter> nodeSignatureStatusMetricMap = new ConcurrentHashMap<>();
    private final Timer cloudStorageLatencyMetric;
    private final Timer downloadLatencyMetric;
    private final Timer streamCloseMetric;
    private final Timer.Builder streamVerificationMetric;

    @SuppressWarnings({"java:S107", "java:S3740"})
    protected Downloader(
            ConsensusNodeService consensusNodeService,
            DownloaderProperties downloaderProperties,
            ImporterProperties importerProperties,
            MeterRegistry meterRegistry,
            DateRangeCalculator dateRangeCalculator,
            NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader,
            StreamFileNotifier streamFileNotifier,
            StreamFileProvider streamFileProvider,
            StreamFileReader<T, ?> streamFileReader) {
        this.consensusNodeService = consensusNodeService;
        this.downloaderProperties = downloaderProperties;
        this.importerProperties = importerProperties;
        this.meterRegistry = meterRegistry;
        this.dateRangeCalculator = dateRangeCalculator;
        this.nodeSignatureVerifier = nodeSignatureVerifier;
        this.signatureFileReader = signatureFileReader;
        this.streamFileProvider = streamFileProvider;
        this.streamFileReader = streamFileReader;
        this.streamFileNotifier = streamFileNotifier;
        this.streamType = downloaderProperties.getStreamType();

        // Metrics
        cloudStorageLatencyMetric = Timer.builder("hiero.mirror.importer.cloud.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was created in the cloud storage provider")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        downloadLatencyMetric = Timer.builder("hiero.mirror.importer.stream.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was downloaded and verified")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        streamCloseMetric = Timer.builder(STREAM_CLOSE_LATENCY_METRIC_NAME)
                .description("The difference between the consensus start of the current and the last stream file")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        streamVerificationMetric = Timer.builder("hiero.mirror.importer.stream.verification")
                .description("The duration in seconds it took to verify consensus and hash chain of a stream file")
                .tag("type", streamType.toString());
    }

    public abstract void download();

    protected void downloadNextBatch() {
        if (!shouldDownload()) {
            return;
        }

        try {
            var sigFilesMap = downloadAndParseSigFiles();

            // Following is a cost optimization to not unnecessarily list the public demo bucket once complete
            if (sigFilesMap.isEmpty()
                    && ImporterProperties.HederaNetwork.DEMO.equalsIgnoreCase(importerProperties.getNetwork())) {
                downloaderProperties.setEnabled(false);
                log.warn("Disabled polling after downloading all files in demo bucket");
            }

            // Verify signature files and download corresponding files of valid signature files
            verifySigsAndDownloadDataFiles(sigFilesMap);
        } catch (SignatureVerificationException e) {
            log.warn(e.getMessage());
        } catch (Exception e) {
            log.error("Error downloading files", e);
        }
    }

    /**
     * Sets the index of the streamFile to the last index plus 1, or 0 if it's the first stream file.
     *
     * @param streamFile the stream file object
     */
    protected void setStreamFileIndex(T streamFile) {
        long index = lastStreamFile
                .get()
                .map(StreamFile::getIndex)
                .map(v -> v + 1)
                .or(() -> Optional.ofNullable(importerProperties.getStartBlockNumber()))
                .orElse(0L);
        streamFile.setIndex(index);
    }

    Multimap<StreamFilename, StreamFileSignature> getStreamFileSignatureMultiMap() {
        // The custom comparator ensures there is no duplicate key-value pairs and randomly sorts the values associated
        // with the same key
        return TreeMultimap.create(Ordering.natural(), STREAM_FILE_SIGNATURE_COMPARATOR);
    }

    protected boolean shouldDownload() {
        return downloaderProperties.isEnabled();
    }

    /**
     * Download and parse all signature files with a timestamp later than the last valid file. Put signature files into
     * a multi-map sorted and grouped by the timestamp.
     *
     * @return a multi-map of signature file objects from different nodes, grouped by filename
     */
    private Multimap<StreamFilename, StreamFileSignature> downloadAndParseSigFiles() {
        // Limit to 1 signature file if downloader is disabled
        long listLimit = downloaderProperties.isEnabled() ? Long.MAX_VALUE : 1;
        var stopwatch = Stopwatch.createStarted();
        var nodes = partialCollection(consensusNodeService.getNodes());
        var startAfterFilename = getStartAfterFilename();
        log.debug("Asking for new signature files created after file: {}", startAfterFilename);

        final var signatures = Objects.requireNonNull(Flux.fromIterable(nodes)
                .flatMap(node -> streamFileProvider
                        .list(node, startAfterFilename)
                        .take(listLimit)
                        .map(s -> {
                            var streamFileSignature = signatureFileReader.read(s);
                            streamFileSignature.setNode(node);
                            streamFileSignature.setStreamType(streamType);
                            return streamFileSignature;
                        })
                        .onErrorContinue((e, s) -> log.error("Error downloading signature files for node {}", node, e)))
                .timeout(downloaderProperties.getCommon().getTimeout())
                .collect(this::getStreamFileSignatureMultiMap, (map, s) -> map.put(s.getFilename(), s))
                .subscribeOn(Schedulers.parallel())
                .block());

        long total = signatures.size();
        if (total > 0) {
            var rate = (int) (1000000.0 * total / stopwatch.elapsed(TimeUnit.MICROSECONDS));
            var counts = signatures.keySet().stream()
                    .limit(10)
                    .collect(Collectors.toMap(
                            Function.identity(), s -> signatures.get(s).size()));
            log.info("Downloaded {} signatures in {} ({}/s): {}", total, stopwatch, rate, counts);
        } else {
            log.info(
                    "No new signature files to download after file: {}. Retrying in {} s",
                    startAfterFilename,
                    downloaderProperties.getFrequency().toMillis() / 1_000f);
        }

        return signatures;
    }

    /**
     * Returns the file name in between the last signature file name that was successfully verified and the next stream
     * file to process in the cloud bucket. On startup, the last signature file name will be the last file successfully
     * imported into the database since all files are downloaded into memory will have been discarded. If startDate or
     * demo network is set, those take precedence.
     *
     * @return filename lexically after the last signature file and before the next stream file
     */
    private StreamFilename getStartAfterFilename() {
        return lastStreamFile
                .get()
                .or(() -> {
                    Optional<StreamFile<I>> streamFile = dateRangeCalculator.getLastStreamFile(streamType);
                    lastStreamFile.compareAndSet(Optional.empty(), streamFile);
                    return streamFile;
                })
                .map(StreamFile::getName)
                .map(StreamFilename::from)
                .orElse(StreamFilename.EPOCH);
    }

    /**
     * For each group of signature files with the same file name: (1) verify that the signature files are signed by
     * corresponding node's PublicKey; (2) For valid signature files, we compare their Hashes to see if at least 1/3 of
     * hashes match. If they do, we download the corresponding data file from a node folder which has valid signature
     * file. (3) compare the hash of data file with Hash which has been agreed on by valid signatures, if match, move
     * the data file into `valid` directory; else download the data file from other valid node folder and compare the
     * hash until we find a match.
     *
     * @param sigFilesMap signature files grouped by filename
     */
    @SuppressWarnings("java:S135")
    private void verifySigsAndDownloadDataFiles(Multimap<StreamFilename, StreamFileSignature> sigFilesMap) {
        var nodeIds = consensusNodeService.getNodes().stream()
                .map(ConsensusNode::getNodeId)
                .collect(Collectors.toSet());
        StreamFilename earliestFilename = null;

        for (var sigFilenameIter = sigFilesMap.keySet().iterator(); sigFilenameIter.hasNext(); ) {
            if (ShutdownHelper.isStopping()) {
                return;
            }

            Instant startTime = Instant.now();
            var sigFilename = sigFilenameIter.next();
            var signatures = sigFilesMap.get(sigFilename);

            if (earliestFilename == null) {
                earliestFilename = sigFilename;
            }

            try {
                nodeSignatureVerifier.verify(signatures);
            } catch (SignatureVerificationException ex) {
                var statusMapMessage = statusMap(signatures, nodeIds);
                if (sigFilenameIter.hasNext()) {
                    log.warn("{}. Trying next group: {}", ex.getMessage(), statusMapMessage);
                    continue;
                }

                throw new SignatureVerificationException(ex.getMessage() + ": " + statusMapMessage);
            }

            boolean valid = verifySignatures(signatures, earliestFilename);
            if (!valid) {
                log.error("None of the data files could be verified, signatures: {}", signatures);
            }

            streamVerificationMetric
                    .tag("success", String.valueOf(valid))
                    .register(meterRegistry)
                    .record(Duration.between(startTime, Instant.now()));
        }
    }

    private boolean verifySignatures(Collection<StreamFileSignature> signatures, StreamFilename earliestFilename) {
        Instant endDate = importerProperties.getEndDate();

        for (var signature : signatures) {
            // Ignore signatures that didn't validate or weren't in the majority
            if (signature.getStatus() != StreamFileSignature.SignatureStatus.CONSENSUS_REACHED) {
                continue;
            }

            var nodeId = signature.getNode().getNodeId();

            try {
                var dataFilename = signature.getDataFilename();
                var node = signature.getNode();
                var streamFileData = Objects.requireNonNull(
                        streamFileProvider.get(dataFilename).block());
                T streamFile = streamFileReader.read(streamFileData);

                verify(streamFile, signature);

                var archiveDestinationFolder = importerProperties.getArchiveDestinationFolderPath(streamFileData);

                if (downloaderProperties.isWriteFiles()) {
                    Utility.archiveFile(streamFileData.getFilePath(), streamFile.getBytes(), archiveDestinationFolder);
                }

                if (downloaderProperties.isWriteSignatures()) {
                    signatures.forEach(s -> Utility.archiveFile(
                            s.getFilename().getBucketFilePath(), s.getBytes(), archiveDestinationFolder));
                }

                if (!downloaderProperties.isPersistBytes()) {
                    streamFile.setBytes(null);
                }

                if (dataFilename.getInstant().isAfter(endDate)) {
                    downloaderProperties.setEnabled(false);
                    log.warn("Disabled polling after downloading all files <= endDate ({})", endDate);
                    return false;
                }

                onVerified(streamFileData, streamFile, node);
                return true;
            } catch (FileOperationException | HashMismatchException | TransientProviderException e) {
                final var previous =
                        lastStreamFile.get().map(StreamFile::getName).orElse("None");
                log.warn(
                        "Failed processing signatures after {} from node {} corresponding to {}. Earliest failure in batch is {}. {}",
                        previous,
                        nodeId,
                        signature.getFilename(),
                        earliestFilename,
                        e.getMessage());
            } catch (Exception e) {
                log.error(
                        "Error downloading data file from node {} corresponding to {}. Will retry another node",
                        nodeId,
                        signature.getFilename(),
                        e);
            }
        }

        return false;
    }

    @SuppressWarnings("java:S1172") // Unused Parameter (node) required by subclass implementations
    protected void onVerified(StreamFileData streamFileData, T streamFile, ConsensusNode node) {
        setStreamFileIndex(streamFile);
        streamFileNotifier.verified(streamFile);

        lastStreamFile.get().ifPresent(last -> {
            long latency = streamFile.getConsensusStart() - last.getConsensusStart();
            streamCloseMetric.record(latency, TimeUnit.NANOSECONDS);
        });

        Instant cloudStorageTime = streamFileData.getLastModified();
        Instant consensusEnd = Instant.ofEpochSecond(0, streamFile.getConsensusEnd());
        cloudStorageLatencyMetric.record(Duration.between(consensusEnd, cloudStorageTime));
        downloadLatencyMetric.record(Duration.between(consensusEnd, Instant.now()));

        // Cache a copy of the streamFile with bytes and items set to null so as not to keep them in memory
        var copy = streamFile.copy().clear();
        lastStreamFile.set(Optional.of(copy));
    }

    /**
     * Verifies the stream file is the next file in the hashchain if it's chained and the hash of the stream file
     * matches the expected hash in the signature.
     *
     * @param streamFile the stream file object
     * @param signature  the signature object corresponding to the stream file
     */
    private void verify(T streamFile, StreamFileSignature signature) {
        String filename = streamFile.getName();
        String expectedPrevHash = lastStreamFile.get().map(StreamFile::getHash).orElse(null);

        if (!verifyHashChain(streamFile, expectedPrevHash)) {
            throw new HashMismatchException(
                    filename, expectedPrevHash, streamFile.getPreviousHash(), HASH_TYPE_RUNNING);
        }

        verifyHash(filename, streamFile.getFileHash(), signature.getFileHashAsHex(), HASH_TYPE_FILE);
        verifyHash(filename, streamFile.getMetadataHash(), signature.getMetadataHashAsHex(), HASH_TYPE_METADATA);
    }

    /**
     * Verifies if the two hashes match.
     *
     * @param filename filename the hash is from
     * @param actual   the actual hash
     * @param expected the expected hash
     */
    private void verifyHash(String filename, String actual, String expected, String hashType) {
        if (!Objects.equals(actual, expected)) {
            throw new HashMismatchException(filename, expected, actual, hashType);
        }
    }

    boolean verifyHashChain(T streamFile, String expectedPreviousHash) {
        if (!streamFile.getType().isChained()) {
            return true;
        }

        if (SHA_384.isHashEmpty(expectedPreviousHash)) {
            log.warn("Previous hash not available");
            return true;
        }

        return streamFile.getPreviousHash().contentEquals(expectedPreviousHash);
    }

    private Map<SignatureStatus, Collection<Long>> statusMap(
            Collection<StreamFileSignature> signatures, Set<Long> nodeIds) {
        Map<SignatureStatus, Collection<Long>> statusMap = signatures.stream()
                .collect(Collectors.groupingBy(
                        StreamFileSignature::getStatus,
                        Collectors.mapping(s -> s.getNode().getNodeId(), Collectors.toCollection(TreeSet::new))));

        var seenNodes = signatures.stream().map(s -> s.getNode().getNodeId()).collect(Collectors.toSet());
        var missingNodes = new TreeSet<>(Sets.difference(nodeIds, seenNodes));
        statusMap.put(SignatureStatus.NOT_FOUND, missingNodes);

        String signatureStreamType = signatures.stream()
                .map(StreamFileSignature::getStreamType)
                .map(StreamType::toString)
                .findFirst()
                .orElse("UNKNOWN");

        for (var entry : statusMap.entrySet()) {
            entry.getValue().forEach(nodeId -> {
                Counter counter = nodeSignatureStatusMetricMap.computeIfAbsent(
                        nodeId, n -> newStatusMetric(nodeId, signatureStreamType, entry.getKey()));
                counter.increment();
            });
        }

        // remove CONSENSUS_REACHED for logging purposes
        statusMap.remove(SignatureStatus.CONSENSUS_REACHED);
        return statusMap;
    }

    private Counter newStatusMetric(Long nodeId, String streamType, SignatureStatus status) {
        return Counter.builder("hiero.mirror.importer.stream.signature.verification")
                .description("The number of signatures verified from a particular node")
                .tag("node", nodeId.toString())
                .tag("type", streamType)
                .tag("status", status.toString())
                .register(meterRegistry);
    }

    /**
     * Returns a randomly-selected (and randomly-ordered) collection of CondensusNode elements from the input, where the
     * total stake is just enough to meet/exceed the CommonDownloader "downloadRatio" property.
     *
     * @param allNodes the entire set of ConsensusNodes
     * @return a randomly-ordered subcollection
     */
    private Collection<ConsensusNode> partialCollection(Collection<ConsensusNode> allNodes) {
        var downloadRatio = downloaderProperties.getCommon().getDownloadRatio();
        // no need to randomize (just return entire list) if # of nodes is 0 or 1 or downloadRatio == 1
        if (allNodes.size() <= 1 || downloadRatio.compareTo(BigDecimal.ONE) == 0) {
            return allNodes;
        }

        var nodes = new ArrayList<>(allNodes);
        // shuffle nodes into a random order
        Collections.shuffle(nodes);

        long totalStake = nodes.getFirst().getTotalStake();
        // only keep "just enough" nodes to reach/exceed downloadRatio
        long neededStake = BigDecimal.valueOf(totalStake)
                .multiply(downloadRatio)
                .setScale(0, RoundingMode.CEILING)
                .longValue();
        long aggregateStake = 0; // sum of the stake of all nodes evaluated so far
        int lastEntry = 0;
        while (aggregateStake < neededStake) {
            aggregateStake += nodes.get(lastEntry++).getStake();
        }

        log.debug(
                "partialCollection: Kept {} of {} nodes, for stake of {} / {}",
                lastEntry,
                allNodes.size(),
                aggregateStake,
                totalStake);
        return nodes.subList(0, lastEntry);
    }
}
