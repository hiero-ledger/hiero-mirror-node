// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import lombok.CustomLog;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.AccumulatorPathVisitor;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.jspecify.annotations.NonNull;

@CustomLog
@Value
public class FileCopier {

    private static final FileFilter ALL_FILTER = f -> true;
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();

    private final Path from;
    private final Path to;
    private final FileFilter dirFilter;
    private final FileFilter fileFilter;

    @NonFinal
    @Setter
    private boolean ignoreNonZeroRealmShard = false;

    private FileCopier(
            @NonNull Path from, @NonNull Path to, @NonNull FileFilter dirFilter, @NonNull FileFilter fileFilter) {
        this.from = from;
        this.to = to;
        this.dirFilter = dirFilter;
        this.fileFilter = fileFilter;
    }

    public static FileCopier create(Path from, Path to) {
        return new FileCopier(from, to, ALL_FILTER, ALL_FILTER);
    }

    public FileCopier from(Path source) {
        return new FileCopier(from.resolve(source), to, dirFilter, fileFilter);
    }

    public FileCopier from(String... source) {
        return from(Paths.get("", source));
    }

    public FileCopier filterDirectories(FileFilter newDirFilter) {
        FileFilter andFilter =
                dirFilter == ALL_FILTER ? newDirFilter : f -> dirFilter.accept(f) || newDirFilter.accept(f);
        return new FileCopier(from, to, andFilter, fileFilter);
    }

    public FileCopier filterDirectories(String wildcardPattern) {
        return filterDirectories(
                WildcardFileFilter.builder().setWildcards(wildcardPattern).get());
    }

    public FileCopier filterFiles(FileFilter newFileFilter) {
        FileFilter andFilter =
                fileFilter == ALL_FILTER ? newFileFilter : f -> fileFilter.accept(f) || newFileFilter.accept(f);
        return new FileCopier(from, to, dirFilter, andFilter);
    }

    public FileCopier filterFiles(String wildcardPattern) {
        return filterFiles(
                WildcardFileFilter.builder().setWildcards(wildcardPattern).get());
    }

    public FileCopier resetTo(final Path target) {
        return new FileCopier(from, target, dirFilter, fileFilter);
    }

    public FileCopier to(Path target) {
        return new FileCopier(from, to.resolve(target), dirFilter, fileFilter);
    }

    public FileCopier to(String... target) {
        return to(Paths.get("", target));
    }

    public void copy() {
        try {
            log.debug("Copying {} to {}", from, to);
            FileFilter combinedFilter = f -> f.isDirectory() ? dirFilter.accept(f) : fileFilter.accept(f);
            FileUtils.copyDirectory(from.toFile(), to.toFile(), combinedFilter);

            if (log.isTraceEnabled()) {
                try (var paths = Files.walk(to)) {
                    paths.forEach(p -> log.trace("Moved: {}", p));
                }
            }

            if (ignoreNonZeroRealmShard || (COMMON_PROPERTIES.getShard() == 0 && COMMON_PROPERTIES.getRealm() == 0)) {
                return;
            }

            var visitor = AccumulatorPathVisitor.builder().get();
            Files.walkFileTree(to, visitor);
            var dirs = visitor.getDirList();
            for (var dir : dirs.subList(0, dirs.size() - 1)) {
                var newDir = getNewDir(dir);
                if (newDir == null) {
                    continue;
                }

                log.debug("Moving {} to {}", dir, newDir);
                FileUtils.moveDirectory(dir.toFile(), newDir);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copy a {@code from} account ID based directory structure to the destination as a node ID based structure.
     *
     * @param destinationAdjuster adjustment to make to {@code to} path. Often {@code to} as been configured to be a
     *                            specific account ID directory stream type, such as {@code accountBalances} or
     *                            {@code recordstreams} etc. Provide a function to adjust this prior to files being
     *                            copied. If no adjustment is required, provide {@link Function#identity()}
     * @param network             Hiero or dev/test network name, typically found in mirror node properties.
     */
    @SneakyThrows
    public void copyAsNodeIdStructure(Function<Path, Path> destinationAdjuster, String network) {
        var destination = destinationAdjuster.apply(to);
        var networkDir = destination.resolve(network);
        var sourceNodeDirs = FileUtils.listFilesAndDirs(from.toFile(), TrueFileFilter.INSTANCE, null);
        long shard = ignoreNonZeroRealmShard ? 0 : COMMON_PROPERTIES.getShard();

        for (var sourceNodeDir : sourceNodeDirs) {
            var sourceNodeDirName = sourceNodeDir.getName();
            for (var streamType : StreamType.values()) {
                var nodePrefix = streamType.getNodePrefix();
                if (StringUtils.isEmpty(nodePrefix) || !sourceNodeDirName.startsWith(nodePrefix)) {
                    continue;
                }

                var nodeAccountId = sourceNodeDirName.substring(nodePrefix.length());
                var nodeEntityId = EntityId.of(nodeAccountId);

                var destinationNodeIdPath = networkDir.resolve(Path.of(
                        String.valueOf(shard),
                        String.valueOf(nodeEntityId.getNum() - 3L), // Node ID
                        streamType.getNodeIdBasedSuffix()));

                FileUtils.copyDirectory(sourceNodeDir, destinationNodeIdPath.toFile());
                break;
            }
        }
    }

    private File getNewDir(Path current) {
        var dirname = current.getFileName().toString();
        var parent = current.getParent();
        if (dirname.matches("^record0\\.0\\.\\d+$")) {
            var prefix = String.format("record%d.%d.", COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm());
            return current.getParent()
                    .resolve(dirname.replace("record0.0.", prefix))
                    .toFile();
        } else if (COMMON_PROPERTIES.getShard() != 0
                && dirname.matches("^\\d+$")
                && parent.getFileName().toString().matches("^\\d+$")) {
            return parent.getParent()
                    .resolve(String.valueOf(COMMON_PROPERTIES.getShard()))
                    .resolve(dirname)
                    .toFile();
        }

        return null;
    }
}
