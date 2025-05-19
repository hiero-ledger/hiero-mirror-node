// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.parser.record.entity.DomainClassComparator.ORDER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.token.DissociateTokenTransfer;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.junit.jupiter.api.Test;

class DomainClassComparatorTest {

    private static final DomainClassComparator COMPARATOR = new DomainClassComparator();

    @Test
    void compare() {
        assertThat(COMPARATOR.compare(Entity.class, TokenAccount.class)).isNegative();
        assertThat(COMPARATOR.compare(TransactionSignature.class, TokenAccount.class))
                .isNegative();
        assertThat(COMPARATOR.compare(Token.class, TokenAccount.class)).isNegative();
        assertThat(COMPARATOR.compare(TokenAccount.class, Token.class)).isPositive();
        assertThat(COMPARATOR.compare(Token.class, Token.class)).isZero();
        assertThat(COMPARATOR.compare(TokenAccount.class, TokenAccount.class)).isZero();
        assertThat(COMPARATOR.compare(TokenAccount.class, DissociateTokenTransfer.class))
                .isNegative();
    }

    @Test
    void compareOrdered() {
        var randomOrder = new ArrayList<>(ORDER);
        Collections.shuffle(randomOrder);
        var sortedOrder = new TreeSet<>(COMPARATOR);
        sortedOrder.addAll(randomOrder);
        assertThat(sortedOrder).containsExactlyElementsOf(ORDER);
    }

    @Test
    void sortedMap() {
        var map = new TreeMap<Class<?>, Integer>(COMPARATOR);
        map.put(TokenAccount.class, 0);
        map.put(Entity.class, 1);
        map.put(CryptoTransfer.class, 2);
        map.put(DissociateTokenTransfer.class, 3);
        map.put(Contract.class, 4);
        assertThat(map.keySet())
                .containsExactly(
                        Contract.class,
                        CryptoTransfer.class,
                        Entity.class,
                        TokenAccount.class,
                        DissociateTokenTransfer.class);
    }
}
