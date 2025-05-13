// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import org.hiero.mirror.restjava.spec.model.SpecSetup;

public interface SpecDomainBuilder {
    void customizeAndPersistEntities(SpecSetup specSetup);
}
