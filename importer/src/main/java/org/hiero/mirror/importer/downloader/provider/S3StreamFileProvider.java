// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.hiero.mirror.importer.domain.StreamFilename.EPOCH;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.StreamType;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.RequestPayer;
import software.amazon.awssdk.services.s3.model.S3Object;

@CustomLog
public final class S3StreamFileProvider extends AbstractStreamFileProvider {

    public static final String SEPARATOR = "/";
    private static final String RANGE_PREFIX = "bytes=0-";
    private static final String TEMPLATE_ACCOUNT_ID_PREFIX = "%s/%s%s/";
    private static final String TEMPLATE_NODE_ID_PREFIX = "%s/%d/%d/%s/";
    private static final String TEMPLATE_BLOCK_STREAM_FILE_PATH = "%d/%d/%s";

    private final Map<PathKey, PathResult> paths = new ConcurrentHashMap<>();
    private final S3AsyncClient s3Client;

    public S3StreamFileProvider(
            CommonProperties commonProperties, CommonDownloaderProperties properties, S3AsyncClient s3Client) {
        super(commonProperties, properties);
        this.s3Client = s3Client;
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        // Number of items we plan do download in a single batch times 2 for file + sig.
        int batchSize = downloaderProperties.getBatchSize() * 2;

        var key = new PathKey(node, lastFilename.getStreamType());
        var pathResult = paths.computeIfAbsent(key, k -> new PathResult());
        var prefix = getPrefix(key, pathResult.getPathType());
        var startAfter = prefix + lastFilename.getFilenameAfter();

        var listRequest = ListObjectsV2Request.builder()
                .bucket(downloaderProperties.getBucketName())
                .prefix(prefix)
                .delimiter(SEPARATOR)
                .startAfter(startAfter)
                .maxKeys(batchSize)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        return Mono.fromFuture(s3Client.listObjectsV2(listRequest))
                .timeout(downloaderProperties.getTimeout())
                .doOnNext(l -> {
                    pathResult.update(!l.contents().isEmpty());
                    log.debug("Returned {} s3 objects", l.contents().size());
                })
                .flatMapIterable(ListObjectsV2Response::contents)
                .filter(r -> r.size() <= downloaderProperties.getMaxSize())
                .map(this::toStreamFilename)
                .filter(s -> s != EPOCH && s.getFileType() == SIGNATURE)
                .flatMapSequential(this::doGet)
                .doOnSubscribe(s -> log.debug(
                        "Searching for the next {} files after {}/{}",
                        batchSize,
                        downloaderProperties.getBucketName(),
                        startAfter))
                .switchIfEmpty(Flux.defer(() -> pathResult.fallback() ? list(node, lastFilename) : Flux.empty()));
    }

    @Override
    protected Mono<StreamFileData> doGet(StreamFilename streamFilename) {
        var s3Key = streamFilename.getFilePath();
        var request = GetObjectRequest.builder()
                .bucket(downloaderProperties.getBucketName())
                .key(s3Key)
                .requestPayer(RequestPayer.REQUESTER)
                .range(RANGE_PREFIX + (downloaderProperties.getMaxSize() - 1))
                .build();
        return Mono.fromFuture(s3Client.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(r -> toStreamFileData(streamFilename, r))
                .timeout(downloaderProperties.getTimeout())
                .onErrorMap(NoSuchKeyException.class, TransientProviderException::new)
                .doOnSuccess(s -> log.debug("Finished downloading {}", s3Key));
    }

    @Override
    protected String getBlockStreamFilePath(long shard, long nodeId, String filename) {
        String filePath = TEMPLATE_BLOCK_STREAM_FILE_PATH.formatted(shard, nodeId, filename);
        return StringUtils.isNotBlank(downloaderProperties.getPathPrefix())
                ? downloaderProperties.getPathPrefix() + SEPARATOR + filePath
                : filePath;
    }

    private String getAccountIdPrefix(PathKey key) {
        var streamType = key.type();
        var nodeAccount = key.node().getNodeAccountId().toString();
        return TEMPLATE_ACCOUNT_ID_PREFIX.formatted(streamType.getPath(), streamType.getNodePrefix(), nodeAccount);
    }

    private String getNodeIdPrefix(PathKey key) {
        var network = downloaderProperties.getImporterProperties().getNetwork();
        var shard = commonProperties.getShard();
        var streamFolder = key.type().getNodeIdBasedSuffix();
        return TEMPLATE_NODE_ID_PREFIX.formatted(network, shard, key.node().getNodeId(), streamFolder);
    }

    private String getPrefix(PathKey key, PathType pathType) {
        var basePrefix =
                switch (pathType) {
                    case ACCOUNT_ID, AUTO -> getAccountIdPrefix(key);
                    case NODE_ID -> getNodeIdPrefix(key);
                };
        return StringUtils.isNotBlank(downloaderProperties.getPathPrefix())
                ? downloaderProperties.getPathPrefix() + SEPARATOR + basePrefix
                : basePrefix;
    }

    private StreamFileData toStreamFileData(StreamFilename streamFilename, ResponseBytes<GetObjectResponse> r) {
        var response = r.response();
        var contentLength = StringUtils.substringAfterLast(response.contentRange(), '/');
        long size = isNumeric(contentLength) ? Long.parseLong(contentLength) : response.contentLength();

        if (size > downloaderProperties.getMaxSize()) {
            throw new InvalidDatasetException("Stream file " + streamFilename + " size " + size + " exceeds limit");
        }

        return new StreamFileData(streamFilename, r::asByteArrayUnsafe, response.lastModified());
    }

    private StreamFilename toStreamFilename(S3Object s3Object) {
        var key = s3Object.key();

        try {
            return StreamFilename.from(key, SEPARATOR);
        } catch (Exception e) {
            log.warn("Unable to parse stream filename for {}", key, e);
            return EPOCH; // Reactor doesn't allow null return values for map(), so use a sentinel that we filter later
        }
    }

    record PathKey(ConsensusNode node, StreamType type) {}

    @Data
    private class PathResult {

        @Nullable
        private volatile Instant expiration;

        private volatile PathType pathType = downloaderProperties.getPathType();

        private PathResult() {
            if (downloaderProperties.getPathType() == PathType.AUTO) {
                this.expiration = Instant.now().plus(downloaderProperties.getPathRefreshInterval());
                this.pathType = PathType.ACCOUNT_ID;
            }
        }

        void update(boolean found) {
            // Path is statically configured or has permanently transitioned from ACCOUNT_ID to NODE_ID
            if (expiration == null) {
                return;
            }

            // Permanently switch to NODE_ID
            if (found && pathType == PathType.NODE_ID) {
                expiration = null;
                return;
            }

            // NODE_ID attempt failed so revert back to ACCOUNT_ID for now
            if (!found && pathType == PathType.NODE_ID) {
                pathType = PathType.ACCOUNT_ID;
                return;
            }

            // If ACCOUNT_ID auto mode interval has expired, try NODE_ID if no files were found
            var now = Instant.now();
            if (now.isAfter(expiration)) {
                expiration = now.plus(downloaderProperties.getPathRefreshInterval());
                if (!found) {
                    pathType = PathType.NODE_ID;
                }
            }
        }

        boolean fallback() {
            return expiration != null && pathType == PathType.NODE_ID;
        }
    }
}
