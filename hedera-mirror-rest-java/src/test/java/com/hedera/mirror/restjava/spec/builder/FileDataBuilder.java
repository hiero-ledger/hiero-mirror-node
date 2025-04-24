// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Named
class FileDataBuilder extends AbstractEntityBuilder<FileData, FileData.FileDataBuilder> {
    private static final List<Long> HEX_ENCODED_FILE_IDS = List.of(111L, 112L);
    private static final Map<String, BiFunction<Object, SpecBuilderContext, Object>> METHOD_PARAMETER_CONVERTERS =
            Map.of("fileData", (value, builderContext) -> {
                var fileId = Long.parseLong(
                        builderContext.specEntity().get("entity_id").toString());
                if (HEX_ENCODED_FILE_IDS.contains(fileId) || !(value instanceof String strValue)) {
                    return HEX_OR_BASE64_CONVERTER.apply(value, builderContext); // TODO clean up parent functions
                }
                return Arrays.copyOfRange(
                        StandardCharsets.UTF_8.encode(strValue).compact().array(), 0, strValue.length());
            });

    FileDataBuilder() {
        super(METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::fileData;
    }

    @Override
    protected FileData.FileDataBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return FileData.builder().transactionType(17);
    }

    @Override
    protected FileData getFinalEntity(FileData.FileDataBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
