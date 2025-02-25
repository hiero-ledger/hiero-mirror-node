// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DefaultSingleton extends AtomicReference<Object> implements SingletonState<Object> {

    private final String key;
}
