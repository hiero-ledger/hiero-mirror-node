# HIP-1137 Block Node Discoverability via On Chain Registry

## Overview

This document describes the design of HIP-1137 Block Node Discoverability in the Hiero Mirror Node.

HIP-1137 introduces on-chain registration of non-consensus service nodes such as block nodes, mirror nodes, and RPC
relays. The Mirror Node ingests the corresponding transactions, persists the state, and exposes the data via REST APIs.

## Goals

- Enhance the database schema to store registered node and associated registered node information
- Ingest `RegisteredNodeCreate`, `RegisteredNodeUpdate`, and `RegisteredNodeDelete` transactions
- Ingest the new `associated_registered_node` field in `NodeCreate` and `NodeUpdate` transactions
- Expose registered node information via REST APIs

## Architecture

### Database

- Add new tables `registered_node` and `registered_node_history`

  ```sql
  create table if not exists registered_node
  (
      admin_key              bytea           null,
      created_timestamp      bigint          null,
      deleted                boolean         default false not null,
      description            varchar(100)    null,
      registered_node_id     bigint          not null,
      service_endpoints      jsonb           null,
      timestamp_range        int8range       not null,
      type                   smallint[]      not null
  );

  alter table if exists registered_node
      add constraint registered_node__pk primary key (registered_node_id);
  create index if not exists registered_node__type
      on registered_node using gin (type) where deleted is false;

  create table if not exists registered_node_history
  (
      like registered_node including defaults
  );

  create index if not exists registered_node_history__node_id_lower_timestamp
      on registered_node_history (registered_node_id, lower(timestamp_range));
  ```

  Note that `service_endpoints` is the JSON serialization of the list of `RegisteredServiceEndpoint` objects.

- Add new column `associated_registered_nodes` to `node` and `node_history` tables
  ```sql
  alter table if exists node add column if not exists associated_registered_nodes bigint[] null;
  alter table if exists node_history add column if not exists associated_registered_nodes bigint[] null;
  ```

### Importer

#### Domain

- Add the following to `AbstractNode` class

  ```java
  public abstract class AbstractNode implements History {

      @JsonSerialize(using = ListToStringSerializer.class)
      private List<Long> associatedRegisteredNodes = Collections.emptyList();
  }
  ```

- Add `AbstractRegisteredNode` class

  ```java
  @Data
  @MappedSuperclass
  @NoArgsConstructor
  @SuperBuilder(toBuilder = true)
  @Upsertable(history = true)
  public abstract class AbstractRegisteredNode implements History {

      public static final short TYPE_BLOCK_NODE = 0;
      public static final short TYPE_GENERAL_SERVICE = 1;
      public static final short TYPE_MIRROR_NODE = 2;
      public static final short TYPE_RPC_RELAY = 3;

      private byte[] adminKey;

      @Column(updatable = false)
      private Long createdTimestamp;

      private boolean deleted;

      private String description;

      @Id
      private Long registeredNodeId;

      @JsonSerialize(using = ObjectToStringSerializer.class)
      @JdbcTypeCode(SqlTypes.JSON)
      private List<RegisteredServiceEndpoint> serviceEndpoints;

      private Range<Long> timestampRange;

      @JsonSerialize(using = ListToStringSerializer.class)
      private List<Short> type = Collectitons.emptyList();
  }
  ```

  Note a registered node's type is the aggregation of all service endpoint's type, defined by the constants.

- Add `RegisteredNode` class and `RegisteredNodeHistory` class

  ```java
  @Data
  @Entity
  public class RegisteredNode extends AbstractRegisteredNode {}
  ```

  ```java
  @Data
  @Entity
  public class RegisteredNodeHistory extends AbstractRegisteredNode {}
  ```

- Add `RegisteredServiceEndpoint` class, `BlockNodeEndpoint` class, `BlockNodeApi` enum, `MirrorNodeEndpoint` class,
  and `RpcRelayEndpoint` class

  ```java
  @Data
  public class RegisteredServiceEndpoint {

      private BlockNodeEndpoint blockNode;
      private String domainName;
      private GeneralServiceEndpoint generalService;
      private String ipAddress;
      private MirrorNodeEndpoint mirrorNode;
      private int port;
      private boolean requiresTls;
      private RpcRelayEndpoint rpcRelay;

      public static class BlockNodeEndpoint {
          private BlockNodeApi endpointApi;
      }

      public enum BlockNodeApi {
          OTHER,
          STATUS,
          PUBLISH,
          SUBSCRIBE_STREAM,
          STATE_PROOF,
          UNRECOGNIZED
      }

      public static class GeneralServiceEndpoint {
          private String description;
      }

      public static class MirrorNodeEndpoint {}

      public static class RpcRelayEndpoint {}
  }
  ```

#### Transaction Parsing

#### EntityListener

- Add `onRegisteredNode()` to handle upserts on the `registered_node` table and the `registered_node_history` table

  ```java
  default void onRegisteredNode(RegisteredNode registeredNode) throws ImporterException {}
  ```

- Implement `onRegisteredNode()` in `SqlEntityListener`. Implement merge logic to preserve existing in-memory values for
  any unchanged properties

#### Transaction Handlers

- Add the following transaction handlers

  - `RegisteredNodeCreateTransactionHandler` for `RegisteredNodeCreate` transactions
  - `RegisteredNodeUpdateTransactionHandler` for `RegisteredNodeUpdate` transactions
  - `RegisteredNodeDeleteTransactionHandler` for `RegisteredNodeDelete` transactions

- Update `NodeCreateTransactionHandler` and `NodeUpdateTransactionHandler` to handle the new
  `associated_registered_node` field in the corresponding transaction body

### REST API

#### Endpoints

- Add `/api/v1/network/registered-nodes`

  ```json
  {
    "registered_nodes": [
      {
        "admin_key": {
          "_type": "ProtobufEncoded",
          "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
        },
        "created_timestamp": "1586567700.453054001",
        "description": "alpha",
        "registered_node_id": 1,
        "service_endpoints": [
          {
            "block_node": {
              "endpoint_api": "STATUS"
            },
            "domain_name": "block1.alpha.com",
            "general_service": null,
            "ip_address": null,
            "mirror_node": null,
            "port": 40840,
            "requires_tls": false,
            "rpc_relay": null,
            "type": "BLOCK_NODE"
          },
          {
            "block_node": {
              "endpoint_api": "SUBSCRIBE_STREAM"
            },
            "domain_name": null,
            "general_service": null,
            "ip_address": "191.91.239.79",
            "mirror_node": null,
            "port": 40842,
            "requires_tls": true,
            "rpc_relay": null,
            "type": "BLOCK_NODE"
          },
          {
            "block_node": null,
            "domain_name": "mirrornode.alpha.com",
            "general_service": null,
            "ip_address": null,
            "mirror_node": {},
            "port": 80,
            "requires_tls": false,
            "rpc_relay": null,
            "type": "MIRROR_NODE"
          }
        ],
        "timestamp": {
          "from": "1586567700.453054001",
          "to": null
        }
      },
      {
        "admin_key": {
          "_type": "ED25519",
          "key": "c67e3c4172e3eea8e4f45714240e453ab8702e7fc13d7ea58e523e6caeb8a38e"
        },
        "created_timestamp": "1586567999.453000201",
        "description": "romeo",
        "registered_node_id": 2,
        "service_endpoints": [
          {
            "block_node": null,
            "domain_name": "mirrornode.romeo.com",
            "general_service": null,
            "ip_address": null,
            "mirror_node": {},
            "port": 80,
            "requires_tls": false,
            "rpc_relay": null,
            "type": "MIRROR_NODE"
          }
        ],
        "timestamp": {
          "from": "1586567999.453000201",
          "to": null
        }
      }
    ],
    "links": {
      "next": "/api/v1/network/registered-nodes?limit=2&registerednode.id=gt:2"
    }
  }
  ```

  Query parameters

  - `limit` - The maximum number of registered nodes to return in the response
  - `order` - The direction to sort the items by the registered node id in the response. Can be `asc` or `desc`, default
    is `asc`
  - `registerednode.id` - The registered node id. Supports `eq`, `gt`, `gte`, `lt`, and `lte` operators. Only one
    occurrence of each operator is allowed. The `eq` operator can't mix with other operators
  - `type` - The type of service provided by the registered node. Supports `BLOCK_NODE`, `GENERAL_SERVICE`,
    `MIRROR_NODE`, and `RPC_RELAY`. Only supports the `eq` operator and only one occurrence is allowed

- Update response of `/api/v1/network/nodes`

  Add `associated_registered_nodes` to the response. It's the list of the ids of the registered nodes associated with a
  consensus node.

  ```json
  {
    "nodes": [
      {
        "associated_registered_nodes": [0, 1, 5, 10],
        ...
        "node_id": 0
      },
      {
        "associated_registered_nodes": [],
        ...
        "node_id": 1
      }
    ],
    "links": {
      "next": "/api/v1/network/nodes?limit=2&node.id=gt:1"
    }
  }
  ```

### Acceptance Tests

Initially the new registered node transactions must be signed by privileged accounts, which prevents the addition of
acceptance tests.

### K6 Tests

Add K6 test cases for the new endpoint `/api/v1/network/registered-nodes`

- list registered nodes with all types
- list registered nodes with `type=BLOCK_NODE`
