// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import static java.lang.invoke.MethodType.methodType;
import static org.awaitility.Awaitility.await;
import static org.springframework.data.util.Predicates.negate;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.gaul.s3proxy.S3Proxy;
import org.gaul.shaded.org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.topic.TopicMessageLookup;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

@UtilityClass
public class TestUtils {

    public static final int S3_PROXY_PORT = 8001;

    private static final SecureRandom RANDOM = new SecureRandom();

    // Customize BeanUtilsBean to not copy properties for null since non-nulls represent partial updates in our system.
    private static final BeanUtilsBean BEAN_UTILS = new BeanUtilsBean() {
        @Override
        public void copyProperty(Object dest, String name, Object value)
                throws IllegalAccessException, InvocationTargetException {
            if (value != null) {
                super.copyProperty(dest, name, value);
            }
        }
    };

    @SuppressWarnings("unchecked")
    public static <T> T clone(T object) {
        try {
            return (T) BEAN_UTILS.cloneBean(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Instant asStartOfEpochDay(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /**
     * Dynamically lookup method references for every getter in object with the given return type
     */
    public static <O, R> Collection<Supplier<R>> gettersByType(O object, Class<?> returnType) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> objectClass = object.getClass();
        Collection<Supplier<R>> getters = new ArrayList<>();

        for (var m : objectClass.getDeclaredMethods()) {
            try {
                if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                MethodType type = MethodType.methodType(returnType, objectClass);
                MethodHandle handle = lookup.unreflect(m);
                if (!handle.type().equals(type)) {
                    continue;
                }

                MethodType functionType = handle.type();
                var function = (Function<O, R>) LambdaMetafactory.metafactory(
                                lookup, "apply", methodType(Function.class), functionType.erase(), handle, functionType)
                        .getTarget()
                        .invokeExact();
                getters.add(() -> function.apply(object));
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return getters;
    }

    public static File getResource(String path) {
        ClassLoader[] classLoaders = {
            Thread.currentThread().getContextClassLoader(),
            Utility.class.getClassLoader(),
            ClassLoader.getSystemClassLoader()
        };
        URL url = null;

        for (ClassLoader classLoader : classLoaders) {
            if (classLoader != null) {
                url = classLoader.getResource(path);
                if (url != null) {
                    break;
                }
            }
        }

        if (url == null) {
            throw new RuntimeException("Cannot find resource: " + path);
        }

        try {
            return new File(url.toURI().getSchemeSpecificPart());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    public static byte[] gzip(byte[] data) {
        try (var bos = new ByteArrayOutputStream();
                var gcos = new GzipCompressorOutputStream(bos)) {
            gcos.write(data);
            gcos.finish();
            return bos.toByteArray();
        }
    }

    public static long id() {
        return RANDOM.nextLong(0, Long.MAX_VALUE);
    }

    public static <T> T merge(T previous, T current) {
        try {
            T merged = clone(previous);
            BEAN_UTILS.copyProperties(merged, current);
            return merged;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<TransactionHash> getShardTransactionHashes(int shard, JdbcTemplate jdbcTemplate) {
        var sql = String.format("SELECT * from transaction_hash_%02d", shard);
        return jdbcTemplate.query(sql, new DataClassRowMapper<>(TransactionHash.class));
    }

    public TransactionHash getTransactionHashFromSqlFunction(JdbcTemplate jdbcTemplate, byte[] hash) {
        var sql = "SELECT * from get_transaction_info_by_hash(?)";
        var prefix = Arrays.copyOfRange(hash, 0, 32);
        var results = jdbcTemplate.query(sql, new DataClassRowMapper<>(TransactionHash.class), prefix);
        return results.stream()
                .filter(t -> Arrays.equals(t.getHash(), hash))
                .findFirst()
                .orElse(null);
    }

    public void insertIntoTransactionHash(JdbcTemplate jdbcTemplate, TransactionHash hash) {
        var sql =
                "INSERT INTO transaction_hash(consensus_timestamp,hash,hash_suffix,payer_account_id) VALUES (?,?,?,?)";
        jdbcTemplate.update(
                sql, hash.getConsensusTimestamp(), hash.getHash(), hash.getHashSuffix(), hash.getPayerAccountId());
    }

    public static long plus(long timestamp, Duration delta) {
        return timestamp + delta.toNanos();
    }

    @SneakyThrows
    public static S3Proxy startS3Proxy(Path path) {
        var properties = new Properties();
        properties.setProperty(
                "jclouds.filesystem.basedir", path.toAbsolutePath().toString());

        try (var context =
                ContextBuilder.newBuilder("filesystem").overrides(properties).build(BlobStoreContext.class)) {
            var s3Proxy = S3Proxy.builder()
                    .blobStore(context.getBlobStore())
                    .endpoint(URI.create("http://localhost:" + S3_PROXY_PORT))
                    .ignoreUnknownHeaders(true)
                    .build();
            s3Proxy.start();

            await("S3Proxy")
                    .dontCatchUncaughtExceptions()
                    .atMost(Duration.ofMillis(500))
                    .pollDelay(Duration.ofMillis(1))
                    .until(() -> AbstractLifeCycle.STARTED.equals(s3Proxy.getState()));
            return s3Proxy;
        }
    }

    public static AccountID toAccountId(String accountId) {
        var parts = accountId.split("\\.");
        return AccountID.newBuilder()
                .setShardNum(Long.parseLong(parts[0]))
                .setRealmNum(Long.parseLong(parts[1]))
                .setAccountNum(Long.parseLong(parts[2]))
                .build();
    }

    public static ContractID toContractId(Entity contract) {
        var contractId =
                ContractID.newBuilder().setShardNum(contract.getShard()).setRealmNum(contract.getRealm());

        if (contract.getEvmAddress() != null) {
            contractId.setEvmAddress(DomainUtils.fromBytes(contract.getEvmAddress()));
        } else {
            contractId.setContractNum(contract.getNum());
        }

        return contractId.build();
    }

    public static EntityTransaction toEntityTransaction(EntityId entityId, RecordItem recordItem) {
        return EntityTransaction.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .entityId(entityId.getId())
                .payerAccountId(recordItem.getPayerAccountId())
                .result(recordItem.getTransactionStatus())
                .type(recordItem.getTransactionType())
                .build();
    }

    public static Map<Long, EntityTransaction> toEntityTransactions(RecordItem recordItem, EntityId... entityIds) {
        return toEntityTransactions(recordItem, Arrays.asList(entityIds), Collections.emptySet());
    }

    public static Map<Long, EntityTransaction> toEntityTransactions(
            RecordItem recordItem, List<EntityId> entityIds, Set<EntityId> excluded) {
        return entityIds.stream()
                .filter(negate(EntityId::isEmpty))
                .filter(negate(excluded::contains))
                .map(id -> toEntityTransaction(id, recordItem))
                .collect(Collectors.toMap(EntityTransaction::getEntityId, Function.identity(), (a, b) -> a));
    }

    public TransactionID toTransactionId(String transactionId) {
        var parts = transactionId.split("-");
        return TransactionID.newBuilder()
                .setAccountID(toAccountId(parts[0]))
                .setTransactionValidStart(toTimestamp(Long.valueOf(parts[1])))
                .build();
    }

    public Timestamp toTimestamp(Long nanosecondsSinceEpoch) {
        if (nanosecondsSinceEpoch == null) {
            return null;
        }
        return Utility.instantToTimestamp(Instant.ofEpochSecond(0, nanosecondsSinceEpoch));
    }

    public Timestamp toTimestamp(long seconds, long nanoseconds) {
        return Timestamp.newBuilder()
                .setSeconds(seconds)
                .setNanos((int) nanoseconds)
                .build();
    }

    public TopicMessageLookup toTopicMessageLookup(String partition, TopicMessage first, TopicMessage last) {
        return TopicMessageLookup.builder()
                .partition(partition)
                .sequenceNumberRange(Range.closedOpen(first.getSequenceNumber(), last.getSequenceNumber() + 1))
                .timestampRange(Range.closedOpen(first.getConsensusTimestamp(), last.getConsensusTimestamp() + 1))
                .topicId(first.getTopicId().getId())
                .build();
    }

    public byte[] toByteArray(Key key) {
        return (null == key) ? null : key.toByteArray();
    }

    public static byte[] generateRandomByteArray(int size) {
        byte[] hashBytes = new byte[size];
        RANDOM.nextBytes(hashBytes);
        return hashBytes;
    }
}
