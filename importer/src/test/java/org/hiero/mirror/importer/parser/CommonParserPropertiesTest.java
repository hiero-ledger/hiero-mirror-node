// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.TransactionFilterFields;
import org.hiero.mirror.importer.exception.InvalidConfigurationException;
import org.hiero.mirror.importer.parser.CommonParserProperties.TransactionFilter;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;

@ExtendWith(MockitoExtension.class)
class CommonParserPropertiesTest {

    private static final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private static final AccountID PAYER =
            AccountID.newBuilder().setAccountNum(1000L).build();
    private static final AccountID RENEWAL =
            AccountID.newBuilder().setAccountNum(1010L).build();
    private static final RecordItem RECORD_ITEM_EXPRESSION = recordItemBuilder
            .cryptoTransfer()
            .transactionBodyWrapper(b -> b.setMemo("MyApp: blah blah")
                    .setTransactionID(TransactionID.newBuilder().setAccountID(PAYER)))
            .build();
    private final CommonParserProperties commonParserProperties = new CommonParserProperties();

    private static Stream<Arguments> filterRecordItemStreamIncludeOrExclude() {
        return Stream.of(
                Arguments.of("0.0.1", recordItemBuilder.consensusSubmitMessage().build(), true),
                Arguments.of("0.0.2", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of("0.0.3", recordItemBuilder.freeze().build(), true),
                Arguments.of("0.0.4", recordItemBuilder.fileCreate().build(), true),
                Arguments.of("0.0.1", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of("0.0.2", recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of("0.0.4", recordItemBuilder.freeze().build(), false),
                Arguments.of(null, recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of("0.0.1", recordItemBuilder.unknown().build(), false),
                Arguments.of(null, recordItemBuilder.unknown().build(), false),
                Arguments.of("0.0.1", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of(
                        "0.0.1/0.0.4/0.0.5", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of(
                        "0.0.2/0.0.4/0.0.5",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false),
                Arguments.of(
                        "0.0.1/0.0.2/0.0.3",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        true),
                Arguments.of(
                        "0.0.1/0.0.2/0.0.3", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of("0.0.3/0.0.4", recordItemBuilder.fileDelete().build(), true),
                Arguments.of("0.0.100", RECORD_ITEM_EXPRESSION, true));
    }

    private static Stream<Arguments> filterRecordItemStreamIncludeAndExclude() {
        return Stream.of(
                Arguments.of("0.0.1", recordItemBuilder.consensusSubmitMessage().build(), true),
                Arguments.of("0.0.2", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of("0.0.3", recordItemBuilder.freeze().build(), false),
                Arguments.of("0.0.4", recordItemBuilder.fileCreate().build(), false),
                Arguments.of("0.0.1", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of("0.0.2", recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of("0.0.4", recordItemBuilder.freeze().build(), false),
                Arguments.of("0.0.5", recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of(
                        "0.0.1/0.0.2",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        true),
                Arguments.of("0.0.2/0.0.4", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of(
                        "0.0.1/0.0.2/0.0.3",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false),
                Arguments.of(
                        "0.0.2/0.0.3/0.0.4", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of("0.0.1/0.0.3", recordItemBuilder.freeze().build(), false),
                Arguments.of("0.0.1/0.0.2", recordItemBuilder.fileCreate().build(), false),
                Arguments.of(
                        "0.0.1/0.0.4/0.0.5", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of(
                        "0.0.2/0.0.4/0.0.5",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false),
                Arguments.of("0.0.1/0.0.2/0.0.4", recordItemBuilder.freeze().build(), false),
                Arguments.of(
                        "0.0.2/0.0.4/0.0.5",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false),
                Arguments.of("0.0.100", RECORD_ITEM_EXPRESSION, false));
    }

    private static Stream<Arguments> filterRecordItemStreamExpressionIncludeOrExclude() {

        return Stream.of(
                Arguments.of(
                        "0.0.1",
                        recordItemBuilder
                                .cryptoTransfer()
                                .transactionBodyWrapper(b -> b.setMemo("MyApp: blah blah"))
                                .build(),
                        true),
                Arguments.of(
                        "0.0.2",
                        recordItemBuilder
                                .cryptoApproveAllowance()
                                .transactionBodyWrapper(b -> b.setTransactionFee(472L)
                                        .setTransactionID(
                                                TransactionID.newBuilder().setAccountID(PAYER)))
                                .build(),
                        true),
                Arguments.of(
                        "0.0.3",
                        recordItemBuilder
                                .contractCreate()
                                .transactionBody(b -> b.setAutoRenewAccountId(RENEWAL))
                                .build(),
                        true));
    }

    @DisplayName("Filter empty")
    @Test
    void filterEmpty() {
        assertThat(commonParserProperties.hasFilter()).isFalse();
        assertThat(commonParserProperties
                        .getFilter()
                        .test(new TransactionFilterFields(
                                entities("0.0.1"),
                                recordItemBuilder.consensusSubmitMessage().build())))
                .isTrue();
        // also test empty filter against a collection of entity ids
        assertThat(commonParserProperties
                        .getFilter()
                        .test(new TransactionFilterFields(
                                entities("0.0.1/0.0.2/0.0.3"),
                                recordItemBuilder.cryptoCreate().build())))
                .isTrue();
        // explicitly test TransactionsFilterFields.EMPTY
        assertThat(commonParserProperties.getFilter().test(TransactionFilterFields.EMPTY))
                .isTrue();
    }

    @DisplayName("Filter using include")
    @ParameterizedTest(name = "with entity {0} and recordItem {1} resulting in {2}")
    @MethodSource("filterRecordItemStreamIncludeOrExclude")
    void filterInclude(String entityId, RecordItem recordItem, boolean result) {
        commonParserProperties.getInclude().add(filter("0.0.1", null, TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", null, TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", null, null));
        commonParserProperties.getInclude().add(filter(null, null, TransactionType.FILECREATE));
        commonParserProperties
                .getInclude()
                .add(filter(
                        null,
                        "transactionBody.transactionID.accountID.accountNum == 1000 && transactionBody.memo.startsWith(\"MyApp\")",
                        null));
        assertThat(commonParserProperties.hasFilter()).isTrue();

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(entities(entityId), recordItem)))
                .isEqualTo(result);
    }

    @DisplayName("Filter expression using include")
    @ParameterizedTest(name = "with entityId {0} recordItem {1} resulting in {2}")
    @MethodSource("filterRecordItemStreamExpressionIncludeOrExclude")
    void filterExpressionInclude(String entityId, RecordItem recordItem, boolean result) {

        commonParserProperties
                .getInclude()
                .add(filter(
                        null, "transactionBody.contractCreateInstance.autoRenewAccountId.accountNum == 1010", null));
        commonParserProperties.getInclude().add(filter(null, "transactionBody.memo.startsWith(\"MyApp\")", null));
        commonParserProperties
                .getInclude()
                .add(filter(
                        null,
                        "transactionBody.transactionID.accountID.accountNum == 1000 && transactionBody.transactionFee > 400 && transactionBody.transactionFee < 500",
                        null));

        assertThat(commonParserProperties.hasFilter()).isTrue();

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(null, recordItem)))
                .isEqualTo(result);
    }

    @DisplayName("Filter using exclude")
    @ParameterizedTest(name = "with entity {0} and recordItem {1} resulting in !{2}")
    @MethodSource("filterRecordItemStreamIncludeOrExclude")
    void filterExclude(String entityId, RecordItem recordItem, boolean notResult) {
        commonParserProperties.getExclude().add(filter("0.0.1", null, TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getExclude().add(filter("0.0.2", null, TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null, null));
        commonParserProperties.getExclude().add(filter(null, null, TransactionType.FILECREATE));
        commonParserProperties
                .getExclude()
                .add(filter(
                        null,
                        "transactionBody.transactionID.accountID.accountNum == 1000 && transactionBody.memo.startsWith(\"MyApp\")",
                        null));

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(entities(entityId), recordItem)))
                .isNotEqualTo(notResult);
    }

    @DisplayName("Filter expression using exclude")
    @ParameterizedTest(name = "with entityId {0} recordItem {1} resulting in !{2}")
    @MethodSource("filterRecordItemStreamExpressionIncludeOrExclude")
    void filterExpressionExclude(String entityId, RecordItem recordItem, boolean notResult) {

        commonParserProperties
                .getExclude()
                .add(filter(
                        null, "transactionBody.contractCreateInstance.autoRenewAccountId.accountNum == 1010", null));
        commonParserProperties.getExclude().add(filter(null, "transactionBody.memo.startsWith(\"MyApp\")", null));
        commonParserProperties
                .getExclude()
                .add(filter(
                        null,
                        "transactionBody.transactionID.accountID.accountNum == 1000 && transactionBody.transactionFee > 400 && transactionBody.transactionFee < 500",
                        null));

        assertThat(commonParserProperties.hasFilter()).isTrue();

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(null, recordItem)))
                .isNotEqualTo(notResult);
    }

    @DisplayName("Filter using include and exclude")
    @ParameterizedTest(name = "with entity {0} and recordItem {1} resulting in {2}")
    @MethodSource("filterRecordItemStreamIncludeAndExclude")
    void filterBoth(String entityId, RecordItem recordItem, boolean result) {
        commonParserProperties.getInclude().add(filter("0.0.1", null, TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", null, TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", null, TransactionType.FREEZE));
        commonParserProperties.getInclude().add(filter("0.0.4", null, TransactionType.FILECREATE));
        commonParserProperties.getInclude().add(filter("0.0.5", null, TransactionType.CONSENSUSCREATETOPIC));
        commonParserProperties.getInclude().add(filter(null, "transactionBody.memo.startsWith(\"MyApp\")", null));

        commonParserProperties.getExclude().add(filter("0.0.2", null, TransactionType.CRYPTOUPDATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null, null));
        commonParserProperties.getExclude().add(filter(null, null, TransactionType.FILECREATE));
        commonParserProperties.getExclude().add(filter("0.0.5", null, TransactionType.CONSENSUSCREATETOPIC));
        commonParserProperties
                .getExclude()
                .add(filter(null, "transactionBody.transactionID.accountID.accountNum == 1000", null));

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(entities(entityId), recordItem)))
                .isEqualTo(result);
    }

    @DisplayName("Invalid filter expression parse exception handling")
    @ParameterizedTest(name = "with expression {0}")
    @CsvSource({"sld&#$$", "transactionBody|consensusTimeStamp ge 32"})
    void filterExpressionParseErrors(String expression) {
        var filter = filter(null, expression, null);
        assertThatThrownBy(filter::getParsedExpression)
                .isInstanceOf(InvalidConfigurationException.class)
                .hasCauseInstanceOf(ParseException.class);
    }

    @DisplayName("Invalid filter expression evaluation exception handling")
    @ParameterizedTest(name = "with expression {0}, {1}")
    @CsvSource({
        "'transactionIndex > 12', Disallowed root RecordItem access",
        "'transaction.bodyBytes.size > 24', Disallowed nested RecordItem access",
        "'transactionRecord.noSuchProperty != null', Unknown property name",
        "'transactionBody.memo.badStringMethod()', Unknown method name",
        "'transactionBody.cryptoApproveAllowance.cryptoAllowances', Does not evaluate to a boolean",
        "'T(util.org.hiero.mirror.importer.Utility).handleRecoverableError(\"Hello from the beyond!\")', Disallowed type access",
        "T(java.lang.Runtime).getRuntime().exec('touch hello.txt') != null, Disallowed type access",
        "systemProperties['user.country'] != null, Disallowed system properties access",
        "'@streamFileProviders.size() > 0', Disallowed bean reference"
    })
    void filterExpressionErrors(String expression, String description) {
        var recordItem = recordItemBuilder.cryptoTransfer().build();

        commonParserProperties.getInclude().add(filter(null, expression, null));
        assertThat(commonParserProperties.hasFilter()).isTrue();

        var transactionFilterFields = new TransactionFilterFields(null, recordItem);
        var filter = commonParserProperties.getFilter();
        assertThatThrownBy(() -> filter.test(transactionFilterFields))
                .as(description)
                .isInstanceOf(InvalidConfigurationException.class)
                .hasCauseInstanceOf(EvaluationException.class);
    }

    private Collection<EntityId> entities(String entityId) {
        if (StringUtils.isBlank(entityId)) {
            return null;
        }

        if (entityId.indexOf("/") > -1) {
            String[] entityIds = entityId.split("/");
            EntityId[] createdEntities = new EntityId[entityIds.length];
            for (int i = 0; i < entityIds.length; i++) {
                createdEntities[i] = EntityId.of(entityIds[i]);
            }
            return Arrays.asList(createdEntities);
        }

        return Collections.singleton(EntityId.of(entityId));
    }

    private TransactionFilter filter(String entity, String expression, TransactionType transaction) {
        var transactionFilter = new TransactionFilter();
        if (StringUtils.isNotBlank(entity)) {
            transactionFilter.setEntity(List.of(EntityId.of(entity)));
        }
        transactionFilter.setExpression(expression);
        if (transaction != null) {
            transactionFilter.setTransaction(List.of(transaction));
        }
        return transactionFilter;
    }
}
