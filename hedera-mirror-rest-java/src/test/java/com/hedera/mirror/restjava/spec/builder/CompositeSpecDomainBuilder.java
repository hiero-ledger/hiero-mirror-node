// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeSpecDomainBuilder implements SpecDomainBuilder {

    private final List<SpecDomainBuilder> specDomainBuilders;

    @Override
    public void customizeAndPersistEntities(SpecSetup specSetup) {
        specDomainBuilders.forEach(specEntityBuilder -> specEntityBuilder.customizeAndPersistEntities(specSetup));
    }
}
