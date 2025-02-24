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
    add column if not exists batch_key bytea default null,
    add column if not exists inner_transactions jsonb default null;
```

### Importer
Create
```java
public record InnerTransaction(
    long payerAccountId,
    long validStartNs
) {}
```

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
    
    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<InnerTransaction> innerTransactions;
    
    public void addInnerTransaction(InnerTransaction innerTransaction) {
        if (innerTransactions == null) {
            innerTransactions = new ArrayList<>();
        }
        else {
          var lastInnerTransaction = innerTransactions.getLast();
          var shouldAddInnerTransaction = lastInnerTransaction.getPayerAccountId() != innerTransaction.getPayerAccountId() ||
                  lastInnerTransaction.getValidStartNs() != innerTransaction.getValidStartNs();
          if (!shouldAddInnerTransaction) {
              return;
          }
        }
       innerTransactions.add(innerTransaction);
    }
}
```

Update
```java
public class EntityRecordItemListener implements RecordItemListener {
    private Transaction buildTransaction(EntityId entityId, RecordItem recordItem) {
        transaction.setBatchKey(body.getBatchKey());
    }
}
```

Update
```java
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {
  @Override
  public void onTransaction(Transaction transaction) throws ImporterException {
    context.add(transaction);
    
    if (transaction.getBatchKey() != null) {
      Transaction batchParent = context.get(Transaction.class, transaction.getParentConsensus());
      
      while (batchParent != null && batchParent.getType() != TransactionType.BATCH) {
        batchParent = context.get(Transaction.class, batchParent.getParentConsensus());
      }
      
      if (batchParent == null) {
          throw new ImporterException("Batch parent not found for transaction: " + transaction.getConsensusTimestamp());
      }
      
      batchParent.addInnerTransaction(new InnerTransaction(transaction.getPayerAccountId(), transaction.getValidStartNs()));
    }
  }

  // ...continue with current logic
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
```psudo
const getTransactionsByIdOrHash = async (req, res) => {
  If request by transactionId 
    Get Transactions by transactionId using current logic
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
- Retrieving transactions by transaction id should have no performance impact for non batch transactions and minimal performance impact for batch transactions

## Open Questions

- Can block streams remove duplication of inner transaction bytes? 

