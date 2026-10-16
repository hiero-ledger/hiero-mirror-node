// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.springframework.data.repository.CrudRepository;

public interface ClprMessageRepository extends CrudRepository<ClprMessage, ClprMessage.Id> {}
