// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import lombok.Data;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.repository.upsert.EntityMetadataRegistry.InformationSchemaColumns;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

class EntityMetadataMappingTest {

    @Test
    void computedIdUsesMappedColumnsAndWritesBackToAggregate() {
        var registry = registry("key_node_id", "key_id", "key_slot", "created", "value");
        var metadata = registry.lookup(ComputedId.class);
        var entity = new ComputedId();
        entity.setOwner(123L);
        entity.setSequence(7);
        entity.setKey(new byte[] {0, 1});
        entity.setCache("not persisted");

        assertThat(metadata.columns(ColumnMetadata::isId, "{0}")).isEqualTo("key_id,key_node_id,key_slot");
        assertThat(metadata.columns(ColumnMetadata::isUpdatable, "{0}")).isEqualTo("value");
        assertThat(metadata.columns("{0}")).isEqualTo("created,key_id,key_node_id,key_slot,value");
        assertThat(column(metadata, "value").getUpsertColumn().shouldCoalesce()).isFalse();
        assertThat(column(metadata, "key_node_id").getGetter().apply(entity)).isEqualTo(123L);
        assertThat(column(metadata, "key_id").getGetter().apply(entity)).isEqualTo(7);
        assertThat(column(metadata, "key_slot").getGetter().apply(entity)).isEqualTo(new byte[] {0, 1});

        // getId() returns a new object: changing it without invoking setId() would silently discard these writes.
        column(metadata, "key_node_id").getSetter().accept(entity, 0L);
        column(metadata, "key_id").getSetter().accept(entity, 0);
        column(metadata, "key_slot").getSetter().accept(entity, new byte[] {2, 3});
        assertThat(entity.getOwner()).isZero();
        assertThat(entity.getSequence()).isZero();
        assertThat(entity.getKey()).containsExactly(2, 3);
        assertThat(entity.getCache()).isEqualTo("not persisted");
        assertThat(registry.lookup(ComputedId.class)).isSameAs(metadata);
    }

    @Test
    void storedEmbeddedIdAccessorsOperateOnTheAggregate() {
        var metadata = registry("key_node_id", "key_id", "key_slot").lookup(StoredId.class);
        var entity = new StoredId();
        assertThat(column(metadata, "key_node_id").getGetter().apply(entity)).isNull();

        column(metadata, "key_node_id").getSetter().accept(entity, 123L);
        column(metadata, "key_id").getSetter().accept(entity, 7);
        assertThat(entity.getId().getOwner()).isEqualTo(123L);
        assertThat(entity.getId().getSequence()).isEqualTo(7);
        assertThat(column(metadata, "key_node_id").getGetter().apply(entity)).isEqualTo(123L);
    }

    @Test
    void scalarPropertyIdIsNotIntrospectedAsACompositeType() {
        var metadata = registry("primary_key").lookup(ScalarId.class);
        var entity = new ScalarId();
        var column = column(metadata, "primary_key");
        assertThat(column.isId()).isTrue();
        assertThat(column.isUpdatable()).isFalse();
        column.getSetter().accept(entity, 0L);
        assertThat(entity.getValue()).isZero();
        assertThat(column.getGetter().apply(entity)).isEqualTo(0L);
    }

    @Test
    void missingMappedColumnFailsBeforeGeneratingSql() {
        assertThatThrownBy(() -> registry("value").lookup(StoredId.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing information schema for key_");
    }

    @Test
    void duplicateColumnsAreNotSilentlyDropped() {
        assertThatThrownBy(() -> registry("id", "value").lookup(DuplicateColumns.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple mapped properties for column value");
    }

    @Test
    void missingIdFailsBeforeGeneratingSql() {
        assertThatThrownBy(() -> registry("value").lookup(MissingId.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No mapped ID columns");
    }

    private ColumnMetadata column(EntityMetadata metadata, String name) {
        return metadata.getColumns().stream()
                .filter(column -> column.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private EntityMetadataRegistry registry(String... columns) {
        var dbProperties = new DBProperties();
        dbProperties.setSchema("public");
        var jdbcOperations = mock(JdbcOperations.class);
        var schema = Arrays.stream(columns)
                .map(name -> {
                    var column = new InformationSchemaColumns();
                    column.setColumnName(name);
                    return column;
                })
                .toList();
        when(jdbcOperations.query(
                        anyString(),
                        ArgumentMatchers.<RowMapper<InformationSchemaColumns>>any(),
                        eq("metadata_test"),
                        eq("public")))
                .thenReturn(schema);
        return new EntityMetadataRegistry(dbProperties, jdbcOperations, new RelationalMappingContext());
    }

    @Data
    static class ParentKey {
        @Column("node_id")
        private long owner;

        @Transient
        private String cache;
    }

    @Data
    static class Key extends ParentKey {
        @Column("id")
        private int sequence;

        @Column("slot")
        private byte[] key;
    }

    @Data
    static class ComputedIdParent {
        @Transient
        private long owner;

        @Transient
        private int sequence;

        @Transient
        private byte[] key;

        @Transient
        private String cache;

        @Id
        @AccessType(AccessType.Type.PROPERTY)
        @Embedded.Nullable(prefix = "key_")
        public Key getId() {
            var id = new Key();
            id.setOwner(owner);
            id.setSequence(sequence);
            id.setKey(key);
            return id;
        }

        public void setId(Key id) {
            owner = id.getOwner();
            sequence = id.getSequence();
            key = id.getKey();
        }
    }

    @Data
    @Table("metadata_test")
    @Upsertable
    static class ComputedId extends ComputedIdParent {
        @InsertOnlyProperty
        private String created;

        @UpsertColumn(shouldCoalesce = false)
        private String value;
    }

    @Data
    @Table("metadata_test")
    @Upsertable
    static class StoredId {
        @Id
        @Embedded.Nullable(prefix = "key_")
        private Key id;
    }

    @Data
    @Table("metadata_test")
    @Upsertable
    static class ScalarId {
        @Transient
        private Long value;

        @Id
        @Column("primary_key")
        @AccessType(AccessType.Type.PROPERTY)
        public Long getId() {
            return value;
        }

        public void setId(Long id) {
            value = id;
        }
    }

    @Data
    @Table("metadata_test")
    @Upsertable
    static class DuplicateColumns {
        @Id
        private Long id;

        @Column("value")
        private String first;

        @Column("value")
        private String second;
    }

    @Data
    @Table("metadata_test")
    @Upsertable
    static class MissingId {
        private String value;
    }
}
