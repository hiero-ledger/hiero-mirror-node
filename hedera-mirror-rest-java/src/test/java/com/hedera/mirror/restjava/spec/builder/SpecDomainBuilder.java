// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.restjava.spec.model.SpecSetup;

public interface SpecDomainBuilder {
    void customizeAndPersistEntities(SpecSetup specSetup);
}
