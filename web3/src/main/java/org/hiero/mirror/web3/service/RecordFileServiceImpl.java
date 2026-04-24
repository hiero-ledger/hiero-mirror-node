// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.validation.HexValidator;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordFileServiceImpl implements RecordFileService {

    private final RecordFileRepository recordFileRepository;

    @Override
    public Optional<RecordFile> findByBlockType(BlockType block) {
        if (block == BlockType.EARLIEST) {
            return recordFileRepository.findEarliest();
        } else if (block == BlockType.LATEST) {
            return recordFileRepository.findLatest();
        } else if (block.isHash()) {
            final var noPrefixHex = Strings.CS.removeStart(block.name().toLowerCase(), HexValidator.HEX_PREFIX);
            if (noPrefixHex.length() != BlockType.RECORD_FILE_HASH_HEX_LENGTH) {
                throw new InvalidParametersException("Invalid block hash length: " + block.name());
            }
            return recordFileRepository.findByHash(noPrefixHex);
        }

        return recordFileRepository.findByIndex(block.number());
    }

    @Override
    public Optional<RecordFile> findByTimestamp(Long timestamp) {
        return recordFileRepository.findByTimestamp(timestamp);
    }
}
