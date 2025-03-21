# HIP-551 Batch Transactions

## Purpose

[HIP-551](https://hips.hedera.com/hip/hip-551) adds the ability to submit batched transactions to the network.
This document details how the mirror node will be updated to support it.

## Goals

- Store relevant batch transaction information in the database
- Enhance the REST api for easy retrieval of batch transactions along with their inner transactions

## Non-Goals

- Handle block streams removing duplication of inner transaction bytes

## Architecture

### Database

#### Transaction

```postgresql
alter table if exists transaction
  add column if not exists batch_key          bytea      default null,
  add column if not exists inner_transactions bigint[][] default null;
```

### Importer

Update

```java
public enum TransactionType {
    BATCH(NEW_BATCH_TYPE_PROTO_ID, EntityOperation.NONE)
}
```

Update

```java
public class Transaction {
  private byte[] batchKey;
  private List<long[]> innerTransactions;

  public void addInnerTransaction(Transaction transaction) {
    if (innerTransactions == null) {
      innerTransactions = new ArrayList<>();
    }

    innerTransactions.add(transaction.toInnerTransaction());
  }

  private long[] toInnerTransaction() {
    return new long []{payerAccountId, validStartNs};
  }

}
```

Update

```java
public class EntityRecordItemListener implements RecordItemListener {
    private Transaction buildTransaction(EntityId entityId, RecordItem recordItem) {
        if (body.hasBatchKey()) {
          transaction.setBatchKey(body.getBatchKey());
        }
    }
}
```

Update

```java
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {
  @Override
  public void onTransaction(Transaction transaction) throws ImporterException {
    context.add(transaction);

    if (transaction.getBatchKey() != null && transaction.getNonce() == 0) {
      Transaction batchParent = context.get(Transaction.class, transaction.getParentConsensus());

      if (batchParent == null) {
        throw new ImporterException("Batch parent not found for transaction: " + transaction.getConsensusTimestamp());
      }
      batchParent.addInnerTransaction(transaction);
    }
    // ...continue with current logic
  }
}
```

Update

```java
public class BlockItem {
  private final TransactionBody transactionBody;
  private final SignatureMap signatureMap;
}
```

Update

```java
public class RecordItem {
  private void parseTransaction() {
    if (transactionBody == null || signatureMap == null) {
      // do current logic
    }
  }
}
```

Update

```java
public class BlockFileTransformer {
    private List<RecordItem> getRecordItems() {
      // Update built record items to
      RecordItem.builder()
              .hapiVersion(hapiVersion)
              .signatureMap(blockItem.getSignatureMap())
              .transaction(blockItem.getTransaction())
              .transactionBody(blockItem.getTransactionBody())
              .transactionIndex(index);
    }
}
```

Update

```java
public class ProtoBlockFileReader implements BlockFileReader {
    private void readEventTransactions(ReaderContext context) {
      // .. current logic
      if (transaction != null) {
        var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
        var transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
        var signatureMap = signedTransaction.getSigMap();
        var blockItem = com.hedera.mirror.common.domain.transaction.BlockItem.builder()
                .transaction(transaction)
                .transactionBody(transactionBody)
                .signatureMap(signatureMap)
                .transactionResult(transactionResult)
                .transactionOutputs(Collections.unmodifiableMap(transactionOutputs))
                .stateChanges(Collections.unmodifiableList(stateChangesList))
                .previous(context.getLastBlockItem())
                .build();
        context.getBlockFile().item(blockItem);
        context.setLastBlockItem(blockItem);
        readBatchTransactions(stateChangesList, context, transactionBody);
      }
    }

    private void readBatchTransactions(ArrayList<StateChanges> stateChangesList,
                                       ReaderContext context,
                                       TransactionBody transactionBody) {
      if (transactionBody.hasAtomicBatchBody()) {
          var signedTransactions = transactionBody.getAtomicBatchBody().getTransactionsList();
          for (var signedTransactions : signedTransactions) {

            // read preceding transactions
            readEventTransactions(context);

            var innerTransactionProto = Transaction.newBuilder()
                    .setSignedTransactionBytes(signedTransaction.toByteString());
            var transactionResult = context.readBlockItemFor(TRANSACTION_RESULT);
            if (transactionResult = null) {
                throw new InvalidStreamFileException(
                        "Expecting inner transaction result");
            }

            var transactionOutputs = new EnumMap<TransactionCase, TransactionOutput>(TransactionCase.class);
            while ((protoBlockItem = context.readBlockItemFor(TRANSACTION_OUTPUT)) != null) {
              var transactionOutput = protoBlockItem.getTransactionOutput();
              transactionOutputs.put(transactionOutput.getTransactionCase(), transactionOutput);
            }

            var blockItem = com.hedera.mirror.common.domain.transaction.BlockItem.builder()
                    .transaction(innerTransactionProto)
                    .transactionBody(TransactionBody.parseFrom(signedTransaction.getBodyBytes()))
                    .signatureMap(signatureMap)
                    .transactionResult(transactionResult)
                    .transactionOutputs(Collections.unmodifiableMap(transactionOutputs))
                    .stateChanges(Collections.unmodifiableList(stateChangesList))
                    .previous(context.getLastBlockItem())
                    .build();

            context.getBlockFile().item(blockItem);
            context.setLastBlockItem(blockItem);

            // read child transactions
            readEventTransactions(context);
          }
      }
    }
}
```

- Record streams can use `UnknownDataTransactionHandler.java` for batch transactions
- Block streams can use `DefaultTransformer.java` for batch transactions

### REST API

#### Transactions

- Change `transaction.js` constructor to add `batch_key` and `inner_transactions`
- Include `batch_key` in transaction response
  - Change `Transactions.formatTransactionRows` to include the `batch_key`
  ```json
  {
    "transactions": [
      {
        "batch_key": {
          "_type": "ED25519",
          "key": "84e9575e7c9c3f5c553fb4c54e5dc408e41a1d58c12388bea37d7d7365320f6f"
        }
      }
    ]
  }
  ```
- Update `Transactions.getTransactionsByIdOrHash` to query all `payer_account_id` and `valid_start_ns` combinations
  contained in `transaction.inner_transactions` for transaction(s) matching the `transactionId`

```pseudo
const getTransactionsByIdOrHash = async (req, res) => {
  If request by transactionId
    Get Transactions by transactionId using current logic
    If scheduled query param is NOT true
      For each transaction in result
        Collect innerTransactions
      If innerTransactions is not empty
        Using initial requestFilters, query for all inner transactions
      If order is asc
        append inner transactions to current result
      Else
        prepend inner transactions to current result
  Use existing result handling
};
```

- Update `transactionResult.js` to include new response codes

```psudo
/**
* The list of batch transactions is empty
*/
BATCH_LIST_EMPTY

/**
* The list of batch transactions contains duplicated transactions
*/
BATCH_LIST_CONTAINS_DUPLICATES

/**
* The list of batch transactions contains null values
*/
BATCH_LIST_CONTAINS_NULL_VALUES

/**
* An inner transaction within the batch failed
*/
INNER_TRANSACTION_FAILED
```

### Acceptance Test Scenarios

- Submit a batch transaction with inner transaction producing hollow create AND child transactions

## Non-Functional Requirements

- The mirror node should be able to handle batch transactions with minimal performance impact on ingest
- Retrieving transactions by transaction id should have no performance impact for non batch transactions and minimal
  performance impact for batch transactions

## Rejected Ideas

- Store inner transactions (with all transaction columns) in a separate table with additional column for batch payer.
  This would allow colocating inner transactions with their batch parent, but would require additional
  storage for duplicated data. Only downside to not taking this approach is additional query overhead when
  retrieving batch transactions by transaction id.
- Store inner transactions as a jsonb column on transaction with array of payer account and valid start. This approach
  was rejected due to potential bloat from using jsonb data type.

## Open Questions

- Can block streams remove duplication of inner transaction bytes?
