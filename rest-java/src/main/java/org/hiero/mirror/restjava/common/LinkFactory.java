// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import com.hedera.mirror.rest.model.Links;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;

public interface LinkFactory {
    <T> Links create(List<T> items, @Nonnull Pageable pageable, @Nonnull Function<T, Map<String, String>> extractor);
}
