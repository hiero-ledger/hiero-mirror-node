// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface FileService {

    SystemFile<ExchangeRateSet> getExchangeRate(Bound timestamp);
}
