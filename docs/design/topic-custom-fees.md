# Topic Custom Fees Design

## Purpose

[HIP-991](https://github.com/hashgraph/hedera-improvement-proposal/blob/master/HIP/hip-991.md) proposes to add a fixed
fee mechanism to the Hedera Consensus Service (HCS) for topic messages, similar to the custom fee structures in the
Hedera Token Service (HTS). This document explains how the mirror node can be updated to support it.

## Goals

- Update / add ingestion logic for the following transactions and persist new information to the database
  - `ConsensusCreateTopic`: custom fees, fee exempt key list, and fee schedule key
  - `ConsensusUpdateTopic`: custom fees, fee exempt key list, and fee schedule key
  - `max_custom_fees` in `TransactionBody`
- Verify assessed custom fees are externalized in transaction record for applicable `ConsensusSubmitMessage`
  transactions persisted to the database, and exposed via JS REST API
- Expose the following topic custom fee information
  - Expose topic custom fees, fee exempt key list, and fee schedule key via JAVA REST API
- Expose max custom fees in transaction body via JS REST API

## Non Goals

## Architecture

### Database

- Add new tables `topic` and `topic_history`

  ```sql
  create table if not exists topic
  (
    admin_key           bytea     null,
    created_timestamp   bigint    null,
    id                  bigint    primary key,
    fee_exempt_key_list bytea     null,
    fee_schedule_key    bytea     null,
    submit_key          bytea     null,
    timestamp_range     int8range not null
  );

  create table topic_history
  (
      like topic including defaults
  );

  -- v2 only, distribution column
  select create_distributed_table('topic', 'id', colocate_with => 'entity');
  select create_distributed_table('topic_history', 'id', colocate_with => 'topic');

  create index if not exists topic_history__timestamp_range on topic_history using gist (timestamp_range);
  create index if not exists topic_history__id_lower_timestamp_range
      on topic_history (id, lower(timestamp_range));
  ```

  Add a database migration to backfill `topic` table from `entity` table. Note for consistency, for all existing topics,
  add a `custom_fee` row with empty custom fees and lower timestamp set to topic's created timestamp.

  The `admin_key` column in `topic` table will hold the value of `key` column in `entity` table, and future topic's
  admin key will only persist to `topic` table. Note the `key` column in `entity` and `entity_history` will remain
  since it's still used by other types of entities.

  The `submit_key` column needs to be dropped from `entity` and `entity_history` table since it's moved to the new
  table.

- Rename column `token_id` to `entity_id` in `custom_fee` and `custom_fee_history` tables

  ```sql
  alter table if exists custom_fee
    rename column token_id to entity_id;
  alter table if exists custom_fee_history
    rename column token_id to entity_id;
  ```

- Add column `max_custom_fees` to table `transaction`
  ```sql
  alter table if exists transaction
    add column if not exists max_custom_fees bytea[];
  ```
  _Note: The field is an array of bytea because it's from the repeated protobuf field `max_custom_fees` in
  `TransactionBody` and without a wrapper message, protobuf only supports serializing the elements one at a time. On the
  other hand, storing it as json will consume much more storage._

## Importer

### Domain

- Add an abstract `AbstractTopic` class. The class implements `History` interface, and has the following fields

  - `adminKey`
  - `createdTimestamp`
  - `id`
  - `feeExemptKeyList`
  - `feeScheduleKey`
  - `submitKey`
  - `timestampRange`

  Add class `Topic` and `TopicHistory` that extends `AbstractTopic` class

- Rename field `tokenId` in `AbstractCustomFee` to `entityId`

- Add `maxCustomFees` to `Transaction`

### Topic Custom Fee Parsing

#### EntityListener

- Add `onTopic()` to handle inserts and updates on `topic` and `topic_history` tables
  ```java
    default void onTopic(Topic topic) throws ImporterException {}
  ```

#### EntityRecordItemListener

- Change `insertAssessedCustomFees()` to process assessed custom fees in transaction record regardless if tokens persist
  property is enabled or not

- Serialize individual `TransactionBody.max_custom_fees` elements and save as a collection of byte arrays
  in `buildTransaction`

#### Transaction Handlers

- In `ConsensusCreateTopicTransactionHandler`, add logic to add a new entry to the `topic` table and a new entry to
  the `custom_fee` table
- In `ConsensusUpdateTopicTransactionHandler`, add logic to create a partial update to the `topic` table only when fee
  exempt key list or fee schedule key or submit key is updated; when custom fees are updated, also add a new entry to
  the `custom_fee` table. It's worth noting that an empty custom fees list in the transaction body clears the custom fee
  schedule, and the handler should insert an entry with empty custom fees to the `custom_fee` table to reflect so.

## REST API

### Endpoints

- `/api/v1/topics/{topicId}`
  - Add topic custom fee fields `custom_fees`, `fee_exempt_key_list`, and `fee_schedule_key` to the response body.
  ```json
  {
    "admin_key": {
      "_type": "ProtobufEncoded",
      "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
    },
    "auto_renew_account": "0.0.2",
    "auto_renew_period": 7776000,
    "created_timestamp": "1586567700.453054000",
    "custom_fees": {
      "created_timestamp": "1586567700.453054000",
      "fixed_fees": [
        {
          "amount": 100,
          "collector_account_id": "0.0.190",
          "denominating_token_id": "0.0.5870"
        }
      ]
    },
    "deleted": false,
    "fee_exempt_key_list": [
      {
        "_type": "ProtobufEncoded",
        "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
      },
      {
        "_type": "ProtobufEncoded",
        "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b39"
      }
    ],
    "fee_schedule_key": {
      "_type": "ProtobufEncoded",
      "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
    },
    "memo": "topic memo",
    "submit_key": {
      "_type": "ProtobufEncoded",
      "key": "421050820e1485acdd59726088e0e4a2130ebbbb70009f640ad95c78dd5a7b38"
    },
    "timestamp": {
      "from": "1586567700.453054000",
      "to": null
    },
    "topic_id": "0.0.2"
  }
  ```

* `/api/v1/transactions`

  - Changes to the body of the response. The response should include the new `max_custom_fees` field to expose any data defined in its corresponding protobuffer definition. A sample response payload follows.

  ```json
  {
    "transactions": [
      {
        "bytes": null,
        "charged_tx_fee": 7,
        "consensus_timestamp": "1234567890.000000007",
        "entity_id": "0.0.2281979",
        "max_custom_fees": [
          {
            "account_id": "0.0.8",
            "amount": 1000,
            "denominating_token_id": null
          },
          {
            "account_id": "0.0.9",
            "amount": 500,
            "denominating_token_id": "0.0.2000"
          }
        ],
        "max_fee": 33,
        "memo_base64": null,
        "name": "CRYPTOTRANSFER",
        "nft_transfers": [
          {
            "is_approval": true,
            "receiver_account_id": "0.0.121",
            "sender_account_id": "0.0.122",
            "serial_number": 1,
            "token_id": "0.0.123"
          },
          {
            "is_approval": true,
            "receiver_account_id": "0.0.321",
            "sender_account_id": "0.0.422",
            "serial_number": 2,
            "token_id": "0.0.123"
          }
        ],
        "node": "0.0.3",
        "nonce": 0,
        "parent_consensus_timestamp": "1234567890.000000007",
        "result": "SUCCESS",
        "scheduled": false,
        "staking_reward_transfers": [
          {
            "account": 3,
            "amount": 150
          },
          {
            "account": 9,
            "amount": 200
          }
        ],
        "transaction_hash": "vigzKe2J7fv4ktHBbNTSzQmKq7Lzdq1/lJMmHT+a2KgvdhAuadlvS4eKeqKjIRmW",
        "transaction_id": "0.0.8-1234567890-000000006",
        "token_transfers": [
          {
            "token_id": "0.0.90000",
            "account": "0.0.9",
            "amount": 1200,
            "is_approval": false
          },
          {
            "token_id": "0.0.90000",
            "account": "0.0.8",
            "amount": -1200,
            "is_approval": false
          }
        ],
        "transfers": [
          {
            "account": "0.0.3",
            "amount": 2,
            "is_approval": false
          },
          {
            "account": "0.0.8",
            "amount": -3,
            "is_approval": false
          },
          {
            "account": "0.0.98",
            "amount": 1,
            "is_approval": false
          },
          {
            "account": "0.0.800",
            "amount": 150,
            "is_approval": false
          },
          {
            "account": "0.0.800",
            "amount": 200,
            "is_approval": false
          }
        ],
        "valid_duration_seconds": 11,
        "valid_start_timestamp": "1234567890.000000006"
      }
    ],
    "links": {
      "next": null
    }
  }
  ```

* `/api/v1/transactions/{id}`
  - Changes to the body of the response. The response should include the new `max_custom_fees` field to expose any data defined in its corresponding protobuffer definition. A sample response payload follows.
  ```json
  {
    "transactions": [
      {
        "assessed_custom_fees": [
          {
            "amount": 100,
            "collector_account_id": "0.0.10",
            "effective_payer_account_ids": ["0.0.8", "0.0.72"],
            "token_id": "0.0.90001"
          }
        ],
        "bytes": null,
        "charged_tx_fee": 7,
        "consensus_timestamp": "1234567890.000000007",
        "entity_id": "0.0.2281979",
        "max_custom_fees": [
          {
            "account_id": "0.0.8",
            "amount": 1000,
            "denominating_token_id": null
          },
          {
            "account_id": "0.0.9",
            "amount": 500,
            "denominating_token_id": "0.0.2000"
          }
        ],
        "max_fee": 33,
        "memo_base64": null,
        "name": "CRYPTOTRANSFER",
        "nft_transfers": [
          {
            "is_approval": true,
            "receiver_account_id": "0.0.121",
            "sender_account_id": "0.0.122",
            "serial_number": 1,
            "token_id": "0.0.123"
          },
          {
            "is_approval": true,
            "receiver_account_id": "0.0.321",
            "sender_account_id": "0.0.422",
            "serial_number": 2,
            "token_id": "0.0.123"
          }
        ],
        "node": "0.0.3",
        "nonce": 0,
        "parent_consensus_timestamp": "1234567890.000000007",
        "result": "SUCCESS",
        "scheduled": false,
        "staking_reward_transfers": [
          {
            "account": 3,
            "amount": 200
          },
          {
            "account": 9,
            "amount": 300
          }
        ],
        "transaction_hash": "vigzKe2J7fv4ktHBbNTSzQmKq7Lzdq1/lJMmHT+a2KgvdhAuadlvS4eKeqKjIRmW",
        "transaction_id": "0.0.8-1234567890-000000006",
        "token_transfers": [
          {
            "token_id": "0.0.90000",
            "account": "0.0.9",
            "amount": 1200,
            "is_approval": true
          },
          {
            "token_id": "0.0.90000",
            "account": "0.0.8",
            "amount": -1200,
            "is_approval": true
          }
        ],
        "transfers": [
          {
            "account": "0.0.3",
            "amount": 2,
            "is_approval": true
          },
          {
            "account": "0.0.8",
            "amount": -3,
            "is_approval": true
          },
          {
            "account": "0.0.98",
            "amount": 1,
            "is_approval": true
          },
          {
            "account": "0.0.800",
            "amount": 200,
            "is_approval": false
          },
          {
            "account": "0.0.800",
            "amount": 300,
            "is_approval": false
          }
        ],
        "valid_duration_seconds": 11,
        "valid_start_timestamp": "1234567890.000000006"
      }
    ]
  }
  ```

### OpenAPI Schema

- Add `FixedCustomFee`, modeled after `FixedFee`, without `all_collectors_are_exempt` field

  ```yaml
  FixedCustomFee:
    type: object
    properties:
      amount:
        example: 100
        format: int64
        type: integer
      collector_account_id:
        $ref: "#/components/schemas/EntityId"
      denominating_token_id:
        $ref: "#/components/schemas/EntityId"
  ```

- Add `ConsensusCustomFees`

  ```yaml
  ConsensusCustomFees:
    type: object
    properties:
      created_timestamp:
        $ref: "#/components/schemas/Timestamp"
      fixed_fees:
        type: array
        items:
          $ref: "#/components/schemas/FixedCustomFee"
  ```

- Add `CustomFeeLimit`
  ```yaml
  CustomFeeLimit:
    type: object
    properties:
      account_id:
        $ref: "#/components/schemas/EntityId"
      amount:
        example: 100
        format: int64
        type: integer
      denominating_token_id:
        $ref: "#/components/schemas/EntityId"
  ```

## Non-Functional Requirements

## Acceptance Tests

Refactor the existing test scenario `Validate Topic message submission` with the following additional steps:

1. Create a fungible token as the custom fee denominating token and validate the token is created successfully
2. Create a topic with custom fees (a fixed fee in HBAR and a fixed fee in fungible token), fee exempt key list,
   and fee schedule key. Validate the topic is created successfully and mirrornode REST API returns correct information
3. ALICE (whose signing key is fee exempted) submits a message to the topic. Validate mirrornode REST API returns the
   transaction with no assessed custom fee, Validate mirrornode REST API returns the topic message
4. Transfer some fungible token to BOB (BOB has unlimited token auto association slots). Validate mirrornode REST API
   returns the crypto transfer transaction
5. BOB submits a message to the topic with max custom fees meeting the custom fee schedule. Validate mirrornode REST
   API returns the transaction with correct assessed custom fees and max custom fees. Validate mirrornode REST API
   returns the topic message
6. Update the topic with empty custom fees, fee exempt key list, and fee schedule key. Validate mirrornode REST API
   returns the updated topic information
7. BOB submits another message to the topic. Validate mirrornode REST API returns the transaction with no assessed
   custom fee. Validate mirrornode REST API returns the topic message

## K6 Tests

Change the topic id in the `topicsId` rest-java k6 test case to one with topic custom fee related properties set.
