// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import java.util.List;
import org.hiero.mirror.common.domain.StreamFile;

public interface StreamFileParser<T extends StreamFile<?>> {

    void parse(T streamFile);

    void parse(List<T> streamFiles);

    ParserProperties getProperties();
}
