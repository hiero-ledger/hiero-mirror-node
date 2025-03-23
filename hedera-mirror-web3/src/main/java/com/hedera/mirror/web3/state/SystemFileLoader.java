// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.SystemEntity;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class SystemFileLoader {

    private final MirrorNodeEvmProperties properties;
    private final V0490FileSchema fileSchema = new V0490FileSchema();
    private final CommonProperties commonProperties;

    @Getter(lazy = true)
    private final Map<FileID, File> systemFiles = loadAll();

    public @Nullable File load(@Nonnull FileID key) {
        return getSystemFiles().get(key);
    }

    private Map<FileID, File> loadAll() {
        var configuration = properties.getVersionedConfiguration();

        var files = List.of(
                load(
                        SystemEntity.FILE_101,
                        Bytes.EMPTY), // Requires a node store but these aren't used by contracts so omit
                load(SystemEntity.FILE_102, Bytes.EMPTY),
                load(SystemEntity.FILE_111, fileSchema.genesisFeeSchedules(configuration)),
                load(SystemEntity.FILE_112, fileSchema.genesisExchangeRates(configuration)),
                load(SystemEntity.FILE_121, fileSchema.genesisNetworkProperties(configuration)),
                load(
                        SystemEntity.FILE_122,
                        Bytes.EMPTY), // genesisHapiPermissions() fails to load files from the classpath
                load(SystemEntity.FILE_123, fileSchema.genesisThrottleDefinitions(configuration)));

        return files.stream().collect(Collectors.toMap(File::fileId, Function.identity()));
    }

    private File load(SystemEntity systemFile, Bytes contents) {
        var fileId = FileID.newBuilder()
                .shardNum(commonProperties.getShard())
                .realmNum(commonProperties.getRealm())
                .fileNum(systemFile.getNum())
                .build();
        return File.newBuilder()
                .contents(contents)
                .deleted(false)
                .expirationSecond(maxExpiry())
                .fileId(fileId)
                .build();
    }

    private long maxExpiry() {
        var configuration = properties.getVersionedConfiguration();
        long maxLifetime = configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        return Instant.now().getEpochSecond() + maxLifetime;
    }
}
