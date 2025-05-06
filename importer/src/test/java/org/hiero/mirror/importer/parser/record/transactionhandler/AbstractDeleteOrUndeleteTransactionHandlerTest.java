// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
abstract class AbstractDeleteOrUndeleteTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final boolean deleteOrUndelete;

    AbstractDeleteOrUndeleteTransactionHandlerTest() {
        this(true);
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecs() {
        String description = deleteOrUndelete
                ? "delete entity transaction, expect entity deleted"
                : "undelete entity transaction, expect entity undeleted";
        AbstractEntity expected = getExpectedEntityWithTimestamp();
        expected.setDeleted(deleteOrUndelete);
        return List.of(UpdateEntityTestSpec.builder()
                .description(description)
                .expected(expected)
                .recordItem(getRecordItem(
                        getDefaultTransactionBody().build(),
                        getDefaultTransactionRecord().build()))
                .build());
    }
}
