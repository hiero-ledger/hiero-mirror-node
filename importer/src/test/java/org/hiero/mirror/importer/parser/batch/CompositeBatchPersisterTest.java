// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class CompositeBatchPersisterTest extends ImporterIntegrationTest {

    private final CompositeBatchPersister compositeBatchInserter;
    private final DomainBuilder domainBuilder;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;

    @Test
    @Transactional
    void persist() {
        Contract contract = domainBuilder.contract().get();
        ContractResult contractResult = domainBuilder.contractResult().get();

        compositeBatchInserter.persist(List.of(contract));
        compositeBatchInserter.persist(List.of(contractResult));

        assertThat(contractRepository.findAll()).containsExactly(contract);
        assertThat(contractResultRepository.findAll()).containsExactly(contractResult);
    }

    @Test
    void persistEmpty() {
        compositeBatchInserter.persist(null);

        Collection<Integer> itemCollection = new ArrayList<Integer>();
        var items = spy(itemCollection);
        when(items.isEmpty()).thenReturn(true);
        compositeBatchInserter.persist(items);
        var it = verify(items, never()).iterator();
        assertThat(it).isNull();
    }

    @Test
    void persistNullItem() {
        List<Object> items = new ArrayList<>();
        items.add(null);
        assertThatThrownBy(() -> compositeBatchInserter.persist(items))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void persistUnknown() {
        List<Object> toPersist = List.of(new Object());
        assertThatThrownBy(() -> compositeBatchInserter.persist(toPersist))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
