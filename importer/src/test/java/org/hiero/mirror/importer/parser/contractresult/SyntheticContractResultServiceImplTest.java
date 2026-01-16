// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractresult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticContractResultServiceImplTest {
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());
    private final EntityProperties entityProperties = new EntityProperties(systemEntity);

    @Mock
    private EntityListener entityListener;

    private SyntheticContractResultService syntheticContractResultService;

    private RecordItem recordItem;
    private EntityId entityTokenId;
    private EntityId senderId;

    @BeforeEach
    void beforeEach() {
        syntheticContractResultService = new SyntheticContractResultServiceImpl(entityListener, entityProperties);
        recordItem = recordItemBuilder.tokenMint(TokenType.FUNGIBLE_COMMON).build();

        TokenID tokenId = recordItem.getTransactionBody().getTokenMint().getToken();
        entityTokenId = EntityId.of(tokenId);
        senderId = EntityId.EMPTY;

        entityProperties.getPersist().setSyntheticContractResults(true);
    }

    @AfterEach
    void afterEach() {
        entityProperties.getPersist().setSyntheticContractResults(false);
    }

    @Test
    @DisplayName("Should be able to create valid synthetic contract result")
    void createValid() {
        syntheticContractResultService.create(new TransferContractResult(recordItem, entityTokenId, senderId));
        verify(entityListener, times(1)).onContractResult(any());
    }

    @Test
    @DisplayName("Should not create synthetic contract result with contract")
    void createWithContract() {
        recordItem = recordItemBuilder.contractCall().build();
        syntheticContractResultService.create(new TransferContractResult(recordItem, entityTokenId, senderId));
        verify(entityListener, times(0)).onContractResult(any());
    }

    @Test
    @DisplayName("Should not create synthetic contract result with entity property turned off")
    void createTurnedOff() {
        entityProperties.getPersist().setSyntheticContractResults(false);
        syntheticContractResultService.create(new TransferContractResult(recordItem, entityTokenId, senderId));
        verify(entityListener, times(0)).onContractResult(any());
    }
}
