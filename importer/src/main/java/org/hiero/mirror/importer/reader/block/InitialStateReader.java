// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import java.util.Collection;
import org.hiero.mirror.common.domain.transaction.RecordFile;

public interface InitialStateReader {

    RecordFile.InitialState read(Collection<StateChanges> initialStateChanges);
}
