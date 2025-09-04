// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.Entity;
import org.junit.jupiter.api.Test;

class ParserContextTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final ParserContext parserContext = new ParserContext();

    @Test
    void add() {
        assertThatThrownBy(() -> parserContext.add(null)).isInstanceOf(NullPointerException.class);
        var domain = domainBuilder.entity().get();
        parserContext.add(domain);

        assertThat(getItems()).containsExactly(List.of(domain));
    }

    @Test
    void addAll() {
        assertThatThrownBy(() -> parserContext.addAll(null)).isInstanceOf(NullPointerException.class);
        var domain1 = List.of(domainBuilder.entity().get());
        var domain2 = List.of(domainBuilder.token().get());
        parserContext.addAll(domain1);
        parserContext.addAll(domain2);
        assertThat(getItems()).containsExactlyInAnyOrder(domain1, domain2);
    }

    @Test
    void clear() {
        parserContext.add(domainBuilder.entity().get());
        parserContext.addEvmAddressLookupId(1L);
        parserContext.clear();
        assertThat(getItems()).isEmpty();
        assertThat(parserContext.getEvmAddressLookupIds()).isEmpty();
    }

    @Test
    void get() {
        assertThat(parserContext.get(Entity.class, 1L)).isNull();
        assertThatThrownBy(() -> parserContext.get(null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> parserContext.get(Entity.class, null)).isInstanceOf(NullPointerException.class);

        var domain = domainBuilder.entity().get();
        parserContext.merge(domain.getId(), domain, (a, b) -> a);
        assertThat(parserContext.get(Entity.class, domain.getId())).isEqualTo(domain);
    }

    @Test
    void getTransient() {
        assertThat(parserContext.getTransient(Entity.class)).isEmpty();
        assertThatThrownBy(() -> parserContext.getTransient(null)).isInstanceOf(NullPointerException.class);

        var domain = domainBuilder.entity().get();
        parserContext.addTransient(domain);
        assertThat(parserContext.getTransient(Entity.class)).containsExactly(domain);
    }

    @Test
    void getAll() {
        assertThat(parserContext.get(Entity.class)).isEmpty();
        assertThatThrownBy(() -> parserContext.get(null)).isInstanceOf(NullPointerException.class);

        var domain = domainBuilder.entity().get();
        parserContext.add(domain);
        assertThat(parserContext.get(Entity.class)).containsExactly(domain);
    }

    @Test
    void remove() {
        parserContext.remove(Entity.class);
        assertThatThrownBy(() -> parserContext.remove(null)).isInstanceOf(NullPointerException.class);
        var domain = domainBuilder.entity().get();
        parserContext.add(domain);
        parserContext.remove(Entity.class);
        assertThat(getItems()).containsExactly(List.of());
    }

    @Test
    void addTransient() {
        assertThatThrownBy(() -> parserContext.addTransient(null)).isInstanceOf(NullPointerException.class);
        var domain = domainBuilder.entity().get();
        parserContext.addTransient(domain);

        assertThat(parserContext.getTransient(Entity.class)).containsExactly(domain);
    }

    @Test
    void addEvmAddressLookupId() {
        assertThatThrownBy(() -> parserContext.getEvmAddressLookupIds().add(0L))
                .isInstanceOf(UnsupportedOperationException.class);
        parserContext.addEvmAddressLookupId(1L);
        parserContext.addEvmAddressLookupId(2L);
        parserContext.addEvmAddressLookupId(1L);
        assertThat(parserContext.getEvmAddressLookupIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    private Collection<Collection<?>> getItems() {
        var items = new ArrayList<Collection<?>>();
        parserContext.forEach(items::add);
        return items;
    }
}
