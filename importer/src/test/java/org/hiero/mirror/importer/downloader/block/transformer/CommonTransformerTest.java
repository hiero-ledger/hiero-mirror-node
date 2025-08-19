// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class CommonTransformerTest extends AbstractTransformerTest {

    private static Stream<Arguments> provideDefaultTransforms() {
        return Stream.of(
                Arguments.of(TransactionType.ATOMIC_BATCH, recordItemBuilder.atomicBatch()),
                Arguments.of(TransactionType.CONSENSUSDELETETOPIC, recordItemBuilder.consensusDeleteTopic()),
                Arguments.of(TransactionType.CONSENSUSUPDATETOPIC, recordItemBuilder.consensusUpdateTopic()),
                Arguments.of(TransactionType.CRYPTOADDLIVEHASH, recordItemBuilder.cryptoAddLiveHash()),
                Arguments.of(TransactionType.CRYPTOAPPROVEALLOWANCE, recordItemBuilder.cryptoApproveAllowance()),
                Arguments.of(TransactionType.CRYPTODELETE, recordItemBuilder.cryptoDelete()),
                Arguments.of(TransactionType.CRYPTODELETEALLOWANCE, recordItemBuilder.cryptoDeleteAllowance()),
                Arguments.of(TransactionType.CRYPTODELETELIVEHASH, recordItemBuilder.cryptoDeleteLiveHash()),
                Arguments.of(TransactionType.CRYPTOUPDATEACCOUNT, recordItemBuilder.cryptoUpdate()),
                Arguments.of(TransactionType.FILEAPPEND, recordItemBuilder.fileAppend()),
                Arguments.of(TransactionType.FILEDELETE, recordItemBuilder.fileDelete()),
                Arguments.of(TransactionType.FILEUPDATE, recordItemBuilder.fileUpdate()),
                Arguments.of(TransactionType.NODEDELETE, recordItemBuilder.nodeDelete()),
                Arguments.of(TransactionType.NODESTAKEUPDATE, recordItemBuilder.nodeStakeUpdate()),
                Arguments.of(TransactionType.NODEUPDATE, recordItemBuilder.nodeUpdate()),
                Arguments.of(TransactionType.SCHEDULEDELETE, recordItemBuilder.scheduleDelete()),
                Arguments.of(TransactionType.SYSTEMDELETE, recordItemBuilder.systemDelete()),
                Arguments.of(TransactionType.SYSTEMUNDELETE, recordItemBuilder.systemUndelete()),
                Arguments.of(TransactionType.TOKENASSOCIATE, recordItemBuilder.tokenAssociate()),
                Arguments.of(TransactionType.TOKENCANCELAIRDROP, recordItemBuilder.tokenCancelAirdrop()),
                Arguments.of(TransactionType.TOKENCLAIMAIRDROP, recordItemBuilder.tokenClaimAirdrop()),
                Arguments.of(TransactionType.TOKENDELETION, recordItemBuilder.tokenDelete()),
                Arguments.of(TransactionType.TOKENDISSOCIATE, recordItemBuilder.tokenDissociate()),
                Arguments.of(TransactionType.TOKENFEESCHEDULEUPDATE, recordItemBuilder.tokenFeeScheduleUpdate()),
                Arguments.of(TransactionType.TOKENGRANTKYC, recordItemBuilder.tokenGrantKyc()),
                Arguments.of(TransactionType.TOKENFREEZE, recordItemBuilder.tokenFreeze()),
                Arguments.of(TransactionType.TOKENPAUSE, recordItemBuilder.tokenPause()),
                Arguments.of(TransactionType.TOKENREJECT, recordItemBuilder.tokenReject()),
                Arguments.of(TransactionType.TOKENREVOKEKYC, recordItemBuilder.tokenRevokeKyc()),
                Arguments.of(TransactionType.TOKENUNFREEZE, recordItemBuilder.tokenUnfreeze()),
                Arguments.of(TransactionType.TOKENUNPAUSE, recordItemBuilder.tokenUnpause()),
                Arguments.of(TransactionType.TOKENUPDATE, recordItemBuilder.tokenUpdate()),
                Arguments.of(TransactionType.TOKENUPDATENFTS, recordItemBuilder.tokenUpdateNfts()),
                Arguments.of(TransactionType.UNCHECKEDSUBMIT, recordItemBuilder.uncheckedSubmit()),
                Arguments.of(TransactionType.UNKNOWN, recordItemBuilder.unknown()));
    }

    @ParameterizedTest(name = "Default transform for {0}")
    @MethodSource("provideDefaultTransforms")
    void defaultTransforms(TransactionType type, RecordItemBuilder.Builder<?> recordItem) {
        // given
        var expectedRecordItem = recordItem.customize(this::finalize).build();
        var blockTransaction =
                blockTransactionBuilder.defaultBlockItem(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void emptyBlockFile() {
        // given
        var blockFile = blockFileBuilder.items(Collections.emptyList()).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).isEmpty());
    }
}
