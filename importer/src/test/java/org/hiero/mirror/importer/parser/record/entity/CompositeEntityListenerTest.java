// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.importer.util.LongListConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CompositeEntityListenerTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock(strictness = Mock.Strictness.LENIENT)
    private EntityListener entityListener1;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private EntityListener entityListener2;

    private CompositeEntityListener compositeEntityListener;
    private EntityProperties entityProperties;

    @BeforeEach
    void setup() {
        entityProperties = new EntityProperties(new SystemEntity(CommonProperties.getInstance()));
        compositeEntityListener =
                new CompositeEntityListener(List.of(entityListener1, entityListener2), entityProperties);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            true, true, '150, 1200', '150, 1200'
            true, false, '150, 1200', '150, 1200'
            false, true, '150, 1200', '150'
            false, false, '150, 1200', ''
            """)
    void onFileData(
            final boolean files,
            final boolean systemFiles,
            @ConvertWith(LongListConverter.class) final List<Long> fileNums,
            @ConvertWith(LongListConverter.class) final List<Long> expectedFileNums) {
        // given
        entityProperties.getPersist().setFiles(files);
        entityProperties.getPersist().setSystemFiles(systemFiles);
        doReturn(true).when(entityListener1).isEnabled();
        doReturn(true).when(entityListener2).isEnabled();

        final var fileDatum = fileNums.stream()
                .map(fileNum -> domainBuilder
                        .fileData()
                        .customize(f -> f.entityId(domainBuilder.entityNum(fileNum)))
                        .get())
                .toList();
        final var expected = fileDatum.stream()
                .filter(fileData ->
                        expectedFileNums.contains(fileData.getEntityId().getNum()))
                .toList();

        // when
        fileDatum.forEach(compositeEntityListener::onFileData);

        // then
        if (expected.isEmpty()) {
            verify(entityListener1, never()).isEnabled();
            verify(entityListener2, never()).isEnabled();
        } else {
            verify(entityListener1, atLeast(1)).isEnabled();
            verify(entityListener2, atLeast(1)).isEnabled();
        }

        var captor = ArgumentCaptor.forClass(FileData.class);
        verify(entityListener1, times(expected.size())).onFileData(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrderElementsOf(expected);

        captor = ArgumentCaptor.forClass(FileData.class);
        verify(entityListener2, times(expected.size())).onFileData(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrderElementsOf(expected);
    }
}
