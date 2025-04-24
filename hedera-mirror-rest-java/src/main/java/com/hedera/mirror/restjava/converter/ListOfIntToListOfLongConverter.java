// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.converter;

import jakarta.inject.Named;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class ListOfIntToListOfLongConverter implements Converter<List<Integer>, List<Long>> {
    @Override
    public List<Long> convert(List<Integer> source) {
        return source.stream().map(Integer::longValue).toList();
    }
}
