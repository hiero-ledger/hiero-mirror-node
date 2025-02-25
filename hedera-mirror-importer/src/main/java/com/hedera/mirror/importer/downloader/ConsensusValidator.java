// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader;

import com.hedera.mirror.importer.domain.StreamFileSignature;
import java.util.Collection;

public interface ConsensusValidator {
    void validate(Collection<StreamFileSignature> signatures);
}
