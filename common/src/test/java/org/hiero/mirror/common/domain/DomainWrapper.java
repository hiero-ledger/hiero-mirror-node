// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import java.util.function.Consumer;

public interface DomainWrapper<T, B> {

    DomainWrapper<T, B> customize(Consumer<B> customizer);

    T get();

    T persist();
}
