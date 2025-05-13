// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.spec.model.SpecSetup;
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
