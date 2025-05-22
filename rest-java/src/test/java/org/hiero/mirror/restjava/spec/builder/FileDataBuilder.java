// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
class FileDataBuilder extends AbstractEntityBuilder<FileData, FileData.FileDataBuilder> {
    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS =
            Map.of("fileData", HEX_OR_BASE64_CONVERTER);

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
