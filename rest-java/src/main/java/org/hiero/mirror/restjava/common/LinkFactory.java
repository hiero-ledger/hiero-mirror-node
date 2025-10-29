// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.hiero.mirror.rest.model.Links;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Pageable;

@NullMarked
public interface LinkFactory {
    <T> Links create(List<T> items, Pageable pageable, Function<T, Map<String, String>> extractor);
}
