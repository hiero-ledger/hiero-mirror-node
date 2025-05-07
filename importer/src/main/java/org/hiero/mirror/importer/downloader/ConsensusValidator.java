// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import java.util.Collection;
import org.hiero.mirror.importer.domain.StreamFileSignature;

public interface ConsensusValidator {
    void validate(Collection<StreamFileSignature> signatures);
}
