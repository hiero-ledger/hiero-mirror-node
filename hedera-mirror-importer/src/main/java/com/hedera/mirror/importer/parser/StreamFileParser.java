// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser;

import com.hedera.mirror.common.domain.StreamFile;
import java.util.List;

public interface StreamFileParser<T extends StreamFile<?>> {

    void parse(T streamFile);

    void parse(List<T> streamFiles);

    ParserProperties getProperties();
}
