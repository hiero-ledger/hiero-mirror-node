// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@NoArgsConstructor
@WritingConverter
public class EntityIdToLongConverter implements Converter<EntityId, Long> {

    @Override
    public Long convert(EntityId source) {
        return source == null || EntityId.isEmpty(source) ? null : source.getId();
    }
}
