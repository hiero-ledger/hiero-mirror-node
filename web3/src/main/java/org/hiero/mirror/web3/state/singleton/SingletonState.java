// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import java.util.function.Supplier;

public interface SingletonState<T> extends Supplier<T> {

    Integer getId();

    default void set(T value) {
        // Do nothing since our singletons are either immutable static data or dynamically retrieved from the db.
    }
}
