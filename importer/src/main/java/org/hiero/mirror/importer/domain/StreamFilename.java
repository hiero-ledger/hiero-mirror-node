// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.SIDECAR;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;

import com.google.common.base.Splitter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.provider.S3StreamFileProvider;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Value
public class StreamFilename implements Comparable<StreamFilename> {

    public static final Comparator<StreamFilename> EXTENSION_COMPARATOR =
            Comparator.comparing(StreamFilename::getExtension);
    public static final StreamFilename EPOCH;
    public static final String SIDECAR_FOLDER = "sidecar";

    private static final Comparator<StreamFilename> COMPARATOR = Comparator.comparing(StreamFilename::getFilename);
    private static final char COMPATIBLE_TIME_SEPARATOR = '_';
    private static final char STANDARD_TIME_SEPARATOR = ':';
    private static final Splitter FILENAME_SPLITTER =
            Splitter.on(FilenameUtils.EXTENSION_SEPARATOR).omitEmptyStrings();
    private static final Pattern SIDECAR_PATTERN;
    private static final Map<StreamType, Map<String, StreamType.Extension>> STREAM_TYPE_EXTENSION_MAP;

    static {
        SIDECAR_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T(\\d{2}_){2}\\d{2}(\\.\\d{1,9})?Z_(\\d{2})\\.");
        Map<StreamType, Map<String, StreamType.Extension>> streamTypeExtensionMap = new EnumMap<>(StreamType.class);
        for (StreamType type : StreamType.values()) {
            Map<String, StreamType.Extension> extensions = new HashMap<>();
            type.getDataExtensions().forEach(ext -> extensions.put(ext.getName(), ext));
            type.getSignatureExtensions().forEach(ext -> extensions.put(ext.getName(), ext));
            streamTypeExtensionMap.put(type, Collections.unmodifiableMap(extensions));
        }
        STREAM_TYPE_EXTENSION_MAP = Collections.unmodifiableMap(streamTypeExtensionMap);
        EPOCH = from("1970-01-01T00_00_00Z.rcd");
    }

    private final String compressor;
    private final StreamType.Extension extension;
    private final String filename;
    private final String pathSeparator;

    // Relative path to directory containing filename, utilizing pathSeparator
    private final String path;
    // The computed file system or bucket relative path for this stream file
    private final String filePath;

    @EqualsAndHashCode.Include
    private final String filenameWithoutCompressor;

    private final FileType fileType;
    private final String fullExtension;
    private final Instant instant;
    private final String sidecarId;
    private final StreamType streamType;
    private final long timestamp = System.currentTimeMillis();

    private StreamFilename(String path, String filename, String pathSeparator) {
        this.pathSeparator = pathSeparator;
        this.filename = filename;
        this.path = path;

        TypeInfo typeInfo = extractTypeInfo(filename);
        this.compressor = typeInfo.compressor;
        this.extension = typeInfo.extension;
        this.fileType = typeInfo.fileType;
        this.sidecarId = typeInfo.sidecarId;
        this.streamType = typeInfo.streamType;
        this.fullExtension = this.compressor == null
                ? this.extension.getName()
                : StringUtils.joinWith(".", this.extension.getName(), this.compressor);

        // A compressed and uncompressed file can exist simultaneously, so we need uniqueness to not include .gz
        this.filenameWithoutCompressor = isCompressed() ? removeExtension(this.filename) : this.filename;
        this.instant = streamType != StreamType.BLOCK
                ? extractInstant(filename, this.fullExtension, this.sidecarId, this.streamType.getSuffix())
                : null;

        var builder = new StringBuilder();
        if (!StringUtils.isEmpty(this.path)) {
            builder.append(this.path);
            builder.append(this.pathSeparator);
        }
        if (this.fileType == SIDECAR) {
            builder.append(SIDECAR_FOLDER);
            builder.append(this.pathSeparator);
        }
        builder.append(this.filename);
        this.filePath = builder.toString();
    }

    public static StreamFilename from(long blockNumber) {
        return from(BlockFile.getFilename(blockNumber, true));
    }

    public static StreamFilename from(String filePath) {
        return from(filePath, S3StreamFileProvider.SEPARATOR);
    }

    public static StreamFilename from(@NonNull String filePath, @NonNull String pathSeparator) {
        var lastSeparatorIndex = filePath.lastIndexOf(pathSeparator);
        var filename = lastSeparatorIndex < 0 ? filePath : filePath.substring(lastSeparatorIndex + 1);
        var path = lastSeparatorIndex < 0 ? null : filePath.substring(0, lastSeparatorIndex);

        return from(path, filename, pathSeparator);
    }

    public static StreamFilename from(@NonNull StreamFilename base, String filename) {
        return from(base.getPath(), filename, base.getPathSeparator());
    }

    public static StreamFilename from(String path, @NonNull String filename, @NonNull String pathSeparator) {
        return new StreamFilename(path, filename, pathSeparator);
    }

    public static String getFilename(StreamType streamType, FileType fileType, Instant instant) {
        String timestamp = instant.toString().replace(STANDARD_TIME_SEPARATOR, COMPATIBLE_TIME_SEPARATOR);
        String suffix = streamType.getSuffix();
        String extension;
        if (fileType == DATA) {
            extension = streamType.getDataExtensions().first().getName();
        } else {
            extension = streamType.getSignatureExtensions().first().getName();
        }

        return StringUtils.joinWith(".", StringUtils.join(timestamp, suffix), extension);
    }

    public Instant getInstant() {
        if (streamType == StreamType.BLOCK) {
            throw new IllegalStateException("BLOCK stream file doesn't have instant in its filename");
        }

        return instant;
    }

    @SuppressWarnings("java:S3776")
    private static TypeInfo extractTypeInfo(String filename) {
        List<String> parts = FILENAME_SPLITTER.splitToList(filename);
        if (parts.size() < 2) {
            throw new InvalidStreamFileException("Failed to determine StreamType for filename: " + filename);
        }

        String last = parts.get(parts.size() - 1);
        String secondLast = parts.get(parts.size() - 2);

        for (StreamType type : StreamType.values()) {
            String suffix = type.getSuffix();
            if (!StringUtils.isEmpty(suffix) && !filename.contains(suffix)) {
                continue;
            }

            String compressor = null;
            String sidecarIndex = null;
            String streamTypeExtension = null;
            Map<String, StreamType.Extension> extensions = STREAM_TYPE_EXTENSION_MAP.get(type);

            if (extensions.containsKey(last)) {
                streamTypeExtension = last;
            } else if (extensions.containsKey(secondLast)) {
                // If the secondLast is stream type extension, the last is the compression extension
                compressor = last;
                streamTypeExtension = secondLast;
            }

            if (streamTypeExtension != null) {
                FileType fileType = streamTypeExtension.endsWith(StreamType.SIGNATURE_SUFFIX) ? SIGNATURE : DATA;
                if (type == StreamType.RECORD && fileType == DATA) {
                    Matcher matcher = SIDECAR_PATTERN.matcher(filename);
                    if (matcher.lookingAt()) {
                        sidecarIndex = matcher.group(matcher.groupCount());
                        fileType = FileType.SIDECAR;
                    }
                }

                return TypeInfo.of(compressor, extensions.get(streamTypeExtension), fileType, sidecarIndex, type);
            }
        }

        throw new InvalidStreamFileException("Failed to determine StreamType for filename: " + filename);
    }

    private static Instant extractInstant(String filename, String fullExtension, String sidecarId, String suffix) {
        try {
            String fullSuffix = StringUtils.join(suffix, ".", fullExtension);
            String dateTime = StringUtils.removeEnd(filename, fullSuffix);
            if (StringUtils.isNotEmpty(sidecarId)) {
                dateTime = StringUtils.removeEnd(dateTime, COMPATIBLE_TIME_SEPARATOR + sidecarId);
            }
            dateTime = dateTime.replace(COMPATIBLE_TIME_SEPARATOR, STANDARD_TIME_SEPARATOR);
            return Instant.parse(dateTime);
        } catch (DateTimeParseException ex) {
            throw new InvalidStreamFileException("Invalid datetime string in filename " + filename, ex);
        }
    }

    @Override
    public int compareTo(StreamFilename other) {
        return COMPARATOR.compare(this, other);
    }

    /**
     * Returns the filename after this file, in the order of timestamp. This is done by removing the separator '.' and
     * extension from the filename, then appending '_', so that regardless of the extension being used, files after the
     * generated filename will always be newer than this file.
     *
     * @return the filename to mark files after this stream filename
     */
    public String getFilenameAfter() {
        return StringUtils.remove(filename, "." + fullExtension) + COMPATIBLE_TIME_SEPARATOR;
    }

    public String getSidecarFilename(int id) {
        if (streamType != StreamType.RECORD || fileType == SIGNATURE) {
            throw new IllegalArgumentException(
                    String.format("%s %s stream doesn't support sidecars", streamType, fileType));
        }

        String end = StringUtils.isEmpty(sidecarId)
                ? "." + fullExtension
                : StringUtils.join(COMPATIBLE_TIME_SEPARATOR, sidecarId, ".", fullExtension);
        return String.format("%s_%02d.%s", StringUtils.removeEnd(filename, end), id, fullExtension);
    }

    public boolean isCompressed() {
        return compressor != null;
    }

    public boolean isNodeId() {
        return !filePath.contains(streamType.getPath());
    }

    @Override
    public String toString() {
        return filename;
    }

    public enum FileType {
        DATA,
        SIDECAR,
        SIGNATURE
    }

    @Value(staticConstructor = "of")
    private static class TypeInfo {
        String compressor;
        StreamType.Extension extension;
        FileType fileType;
        String sidecarId;
        StreamType streamType;
    }
}
