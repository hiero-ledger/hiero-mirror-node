// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.hiero.mirror.common.domain.StreamType.BLOCK;
import static org.hiero.mirror.importer.domain.StreamFilename.EPOCH;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;

import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.jspecify.annotations.NullMarked;
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
@NullMarked
public final class S3StreamFileProvider extends AbstractStreamFileProvider {

    public static final String SEPARATOR = "/";
    private static final String RANGE_PREFIX = "bytes=0-";
    private static final String TEMPLATE_ACCOUNT_ID_PREFIX = "%s/%s%s/";

    private final BlockProperties blockProperties;
    private final S3AsyncClient s3Client;

    public S3StreamFileProvider(
            final BlockProperties blockProperties,
            final CommonDownloaderProperties downloaderProperties,
            final S3AsyncClient s3Client) {
        super(downloaderProperties);
        this.blockProperties = blockProperties;
        this.s3Client = s3Client;
    }

    @Override
    protected Flux<String> doDiscoverNetwork() {
        final var listRequest = ListObjectsV2Request.builder()
                .bucket(blockProperties.getBucketName())
                .delimiter(SEPARATOR)
                .maxKeys(downloaderProperties.getBatchSize() * 4)
                .requestPayer(RequestPayer.REQUESTER)
                .build();
        return Mono.fromFuture(s3Client.listObjectsV2(listRequest))
                .timeout(downloaderProperties.getTimeout())
                .doOnNext(r -> log.debug(
                        "Returned {} common prefixes", r.commonPrefixes().size()))
                .flatMapIterable(ListObjectsV2Response::commonPrefixes)
                .mapNotNull(commonPrefix -> StringUtils.substringBeforeLast(commonPrefix.prefix(), SEPARATOR));
    }

    @Override
    public Flux<StreamFileData> list(final ConsensusNode node, final StreamFilename lastFilename) {
        // Number of items we plan do download in a single batch times 2 for file + sig.
        int batchSize = downloaderProperties.getBatchSize() * 2;

        var prefix = getPrefix(node, lastFilename.getStreamType());
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
                .doOnNext(l -> log.debug("Returned {} s3 objects", l.contents().size()))
                .flatMapIterable(ListObjectsV2Response::contents)
                .filter(r -> r.size() <= downloaderProperties.getMaxSize())
                .map(this::toStreamFilename)
                .filter(s -> s != EPOCH && s.getFileType() == SIGNATURE)
                .flatMapSequential(this::get)
                .doOnSubscribe(s -> log.debug(
                        "Searching for the next {} files after {}/{}",
                        batchSize,
                        downloaderProperties.getBucketName(),
                        startAfter));
    }

    @Override
    public Mono<StreamFileData> get(final StreamFilename streamFilename) {
        final var bucketName = streamFilename.getStreamType() != BLOCK
                ? downloaderProperties.getBucketName()
                : blockProperties.getBucketName();
        final var s3Key = streamFilename.getBucketFilePath();
        final var request = GetObjectRequest.builder()
                .bucket(bucketName)
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

    private String getPrefix(ConsensusNode node, StreamType streamType) {
        var nodeAccount = node.getNodeAccountId().toString();
        var basePrefix =
                TEMPLATE_ACCOUNT_ID_PREFIX.formatted(streamType.getPath(), streamType.getNodePrefix(), nodeAccount);
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
}
