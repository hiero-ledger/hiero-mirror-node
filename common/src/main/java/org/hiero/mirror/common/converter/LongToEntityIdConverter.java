// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@NoArgsConstructor
@ReadingConverter
public class LongToEntityIdConverter implements Converter<Long, EntityId> {

    @Override
    public EntityId convert(Long source) {
        return source == null ? null : EntityId.of(source);
    }
}
