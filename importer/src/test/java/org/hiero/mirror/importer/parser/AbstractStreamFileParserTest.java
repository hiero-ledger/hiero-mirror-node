// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.importer.db.DiskSpaceService;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.repository.StreamFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({OutputCaptureExtension.class, MockitoExtension.class})
public abstract class AbstractStreamFileParserTest<F extends StreamFile<?>, T extends StreamFileParser<F>> {

    @Mock(strictness = LENIENT)
    protected DiskSpaceService diskSpaceService;

    protected T parser;

    protected ParserProperties parserProperties;

    protected abstract T getParser();

    protected abstract F getStreamFile();

    protected abstract StreamFileRepository<F, ?> getStreamFileRepository();

    protected abstract void mockDbFailure(ParserException e);

    @BeforeEach()
    public void before() {
        when(diskSpaceService.isExceeded()).thenReturn(false);
        parser = getParser();
        parserProperties = parser.getProperties();
        parserProperties.setEnabled(true);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void parse(boolean startAndEndSame) {
        // given
        F streamFile = getStreamFile();
        if (startAndEndSame) {
            streamFile.setConsensusStart(streamFile.getConsensusStart());
        }

        // when
        parser.parse(streamFile);

        // then
        assertParsed(streamFile, true, false);
    }

    @Test
    void disabled() {
        // given
        parserProperties.setEnabled(false);
        F streamFile = getStreamFile();

        // when
        parser.parse(streamFile);

        // then
        assertParsed(streamFile, false, false);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void alreadyExists(boolean startAndEndSame) {
        // given
        F streamFile = getStreamFile();
        if (startAndEndSame) {
            streamFile.setConsensusStart(streamFile.getConsensusStart());
        }
        when(getStreamFileRepository().findLatest()).thenReturn(Optional.of(streamFile));

        // when
        parser.parse(streamFile);

        // then
        assertParsed(streamFile, false, false);
    }

    @Test
    void diskSpaceFull() {
        // given
        when(diskSpaceService.isExceeded()).thenReturn(true);
        when(diskSpaceService.getCheckFrequency()).thenReturn(Duration.ZERO);
        F streamFile = getStreamFile();

        // when / then
        assertThatThrownBy(() -> parser.parse(streamFile)).isInstanceOf(ParserException.class);
    }

    @Test
    void failureShouldRollback(CapturedOutput output) {
        // given
        F streamFile = getStreamFile();
        var e = new ParserException("boom");
        mockDbFailure(e);

        // when
        assertThatThrownBy(() -> parser.parse(streamFile)).isEqualTo(e);

        // then
        assertParsed(streamFile, false, true);
        assertThat(output.getOut()).contains("Error parsing file").contains(e.getMessage());
    }

    protected void assertParsed(F streamFile, boolean parsed, boolean dbError) {
        if (!dbError) {
            assertThat(streamFile.getBytes()).isNull();
            assertThat(streamFile.getItems()).isEmpty();
        } else {
            assertThat(streamFile.getBytes()).isNotNull();
            assertThat(streamFile.getItems()).isNotEmpty();
        }
    }
}
