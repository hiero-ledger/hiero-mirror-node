// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.StreamFile;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface StreamFileRepository<T extends StreamFile<?>, I> extends CrudRepository<T, I> {

    Optional<T> findLatest();
}
