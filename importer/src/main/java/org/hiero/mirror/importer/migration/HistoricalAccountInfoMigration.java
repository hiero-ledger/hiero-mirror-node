// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import jakarta.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
public class HistoricalAccountInfoMigration extends RepeatableMigration {

    static final Instant EXPORT_DATE = Instant.parse("2019-09-14T00:00:10Z");

    private final Set<Long> contractIds = new HashSet<>();
    private final ObjectProvider<EntityRepository> entityRepositoryProvider;
    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ImporterProperties importerProperties;

    @Value("classpath:accountInfoContracts.txt")
    private Resource accountInfoContracts;

    @Value("classpath:accountInfo.txt.gz")
    private Resource accountInfoPath;

    public HistoricalAccountInfoMigration(
            ObjectProvider<EntityRepository> entityRepositoryProvider,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.entityRepositoryProvider = entityRepositoryProvider;
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.importerProperties = importerProperties;
    }

    @Override
    public String getDescription() {
        return "Import historical account information from before open access";
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (!migrationProperties.isEnabled()) {
            log.info("Skip migration since it's disabled");
            return true;
        }

        return false; // Migrate for both v1 and v2
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!importerProperties.isImportHistoricalAccountInfo()) {
            log.info("Skipping migration since importing historical account information is disabled");
            return;
        }

        if (!ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(importerProperties.getNetwork())) {
            log.info("Skipping migration since it only applies to mainnet");
            return;
        }

        Instant startDate = Objects.requireNonNullElseGet(importerProperties.getStartDate(), Instant::now);
        if (startDate.isAfter(EXPORT_DATE)) {
            log.info("Skipping migration since start date {} is after the export date {}", startDate, EXPORT_DATE);
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        loadContractIds();
        fixContractEntities();
        processFile(stopwatch);
    }

    private void processFile(Stopwatch stopwatch) throws IOException {
        log.info("Importing historical account information");

        try (BufferedReader reader = toReader(new GZIPInputStream(accountInfoPath.getInputStream()))) {
            long count = reader.lines()
                    .map(this::parse)
                    .filter(Objects::nonNull)
                    .map(this::process)
                    .filter(Boolean::booleanValue)
                    .count();
            log.info("Successfully updated {} accounts in {}", count, stopwatch);
        }
    }

    private BufferedReader toReader(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private void loadContractIds() throws IOException {
        try (BufferedReader reader = toReader(accountInfoContracts.getInputStream())) {
            reader.lines().filter(StringUtils::isNotBlank).map(Long::parseLong).forEach(contractIds::add);
            log.info("Loaded {} contract IDs", contractIds.size());
        }
    }

    private void fixContractEntities() {
        int inserted = jdbcOperationsProvider
                .getObject()
                .update(
                        "update entity set type = 'CONTRACT' where id in (:ids)",
                        new MapSqlParameterSource("ids", contractIds));
        log.info("Changed {} entity to be type contract", inserted);

        inserted = jdbcOperationsProvider
                .getObject()
                .update(
                        "update entity_history set type = 'CONTRACT' where id in (:ids)",
                        new MapSqlParameterSource("ids", contractIds));
        log.info("Changed {} entity_history to be type contract", inserted);
    }

    private AccountInfo parse(String line) {
        try {
            if (StringUtils.isNotBlank(line)) {
                byte[] data = Base64.decodeBase64(line);
                return AccountInfo.parseFrom(data);
            }
        } catch (Exception e) {
            log.error("Unable to parse AccountInfo from line: {}", line, e);
        }

        return null;
    }

    @SuppressWarnings({"deprecation", "java:S1874", "java:S3776"})
    boolean process(AccountInfo accountInfo) {
        EntityType entityType = EntityType.ACCOUNT;
        long id = EntityId.of(accountInfo.getAccountID()).getId();

        if (contractIds.contains(id)) {
            entityType = EntityType.CONTRACT;
        }

        EntityId entityId = EntityId.of(id);
        Optional<Entity> currentEntity = entityRepositoryProvider.getObject().findById(entityId.getId());
        boolean exists = currentEntity.isPresent();

        Entity entity = currentEntity.orElseGet(entityId::toEntity);
        entity.setType(entityType);
        boolean updated = !exists;

        // All regular accounts have a key so if it's missing we know it had to have been created before the reset.
        // All contract accounts don't have to have a key, but luckily in our file they do.
        if (exists && ArrayUtils.isNotEmpty(entity.getKey())) {
            log.trace("Skipping entity {} that was created after the reset", entityId);
            return false;
        }

        if (entity.getAutoRenewPeriod() == null && accountInfo.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(accountInfo.getAutoRenewPeriod().getSeconds());
            updated = true;
        }

        if (entity.getDeclineReward() == null) {
            entity.setDeclineReward(false);
        }

        // Accounts can't be undeleted
        if (entity.getDeleted() == null
                || (entity.getDeleted() != accountInfo.getDeleted() && accountInfo.getDeleted())) {
            entity.setDeleted(accountInfo.getDeleted());
            updated = true;
        }

        if (entity.getExpirationTimestamp() == null && accountInfo.hasExpirationTime()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(accountInfo.getExpirationTime()));
            updated = true;
        }

        if (entity.getKey() == null && accountInfo.hasKey()) {
            entity.setKey(accountInfo.getKey().toByteArray());
            updated = true;
        }

        if (StringUtils.isEmpty(entity.getMemo())) {
            entity.setMemo(accountInfo.getMemo());
            updated = true;
        }

        if (entity.getProxyAccountId() == null && accountInfo.hasProxyAccountID()) {
            EntityId proxyAccountEntityId = EntityId.of(accountInfo.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountEntityId); // Proxy account should get created separately
            updated |= proxyAccountEntityId != null;
        }

        if (updated) {
            log.info("Saving {} entity: {}", exists ? "existing" : "new", entity);
            entityRepositoryProvider.getObject().save(entity);
        }

        return updated;
    }
}
