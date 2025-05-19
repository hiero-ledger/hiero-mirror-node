// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import com.hedera.hapi.node.base.SemanticVersion;
import jakarta.inject.Named;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class SemanticVersionConvertor implements Converter<String, SemanticVersion> {

    private final com.hedera.node.config.converter.SemanticVersionConverter delegate =
            new com.hedera.node.config.converter.SemanticVersionConverter();

    @Override
    public SemanticVersion convert(String source) {
        return delegate.convert(source);
    }
}
