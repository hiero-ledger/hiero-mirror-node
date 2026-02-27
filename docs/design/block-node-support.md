# HIP-1081 Block Node

## Purpose

[HIP-1081](https://hips.hedera.com/hip/hip-1081) introduces block node, a new node type that is designed to parse the block stream, store latest state
and serve up additional network APIs such as state proofs and block retrieval that support decentralized querying
and verification of state.

This design document concerns the support of streaming blocks from block nodes in mirror nodes.

## Goals

- Support streaming blocks from block nodes
- Support automatically switching the source from cloud storage to block nodes

## Non-Goals

- Removal of downloading block stream files from cloud storage
- Retrieve a state snapshot and bootstrap

## Architecture

### Database

For blocks streamed from block nodes, it's an expensive operation to get the serialized protobuf message size given
there can be tens of thousands block items in a block. As a result, there won't be `size` info, and the `size` column
will have `null` value.

```sql
alter table if exists record_file alter column node_id drop not null;
```

_Update_: In V2, `node_id` is in `record_file` table's primary key. As a result, it can't be nullable. For blocks
streamed from block nodes, the `node_id` will always be -1 instead. Furthermore, block nodes will have unique node ids
as proposed by a pending HIP, so we can use the registered node id once it's implemented.

### Data Flow

![Data Flow](images/block-node-support.svg)

Note: The diagram amends the block streams data flow with block node support

## Importer

- Add new configuration properties to `BlockStreamProperties` to support block nodes
- Replace interface `StreamPoller` with `BlockStreamSource`
- Add an abstract class `AbstractBlockStreamSource`
- Move block file downloading in `BlockStreamPoller` to a new class `BlockFileSource`
- Add new class `BlockNodeSubscriber` to support streaming blocks from block nodes
- Rename `BlockFileReader` to `BlockStreamReader`, and `ProtoBlockFileReader` to `ProtoBlockStreamReader`

### Interfaces and Classes

### BlockNodeProperties

```java
public class BlockNodeProperties {
    private String host;
    private int statusPort;
    private int streamingPort;
    private int priority;
}
```

The `statusPort` is used for the block node status API, while the `streamingPort` is used for the streaming API.
Both ports default to 40840 initially.

When picking a block node to stream block items from, a block node with higher `priority` is always tried first. Block
nodes with the same `priority` are tried with the order in the configuration. `priority` can't be negative and 0 is the
highest.

### BlockSourceType

```java
public enum BlockSourceType {
    AUTO,
    BLOCK_NODE,
    FILE
}
```

See [`CompositeBlockSource`](#CompositeBlockSource) for how `AUTO` works.

### BlockProperties

```java
public class BlockProperties {
    ...
    private Collection<BlockNodeProperties> nodes = Collections.emptyList();
    private BlockSourceType sourceType = BlockSourceType.AUTO;
    private StreamProperties stream = new StreamProperties();
}
```

This is renamed from `BlockPollerProperties`, with three new properties added.

### StreamProperties

```java
public class StreamProperties {
    private int maxBlockItems = 800_000;
    private DataSize maxStreamResponseSize = DataSize.ofMegabytes(8);
    private int maxSubscribeAttempts = 3;
    private Duration readmitDelay = Duration.ofMinutes(1);
    private Duration responseTimeout = Duration.ofMillis(400);
}
```

`StreamProperties` is the place for properties common to block nodes

- `maxBlockItems`: The max number of block items allowed in a block streamed from block nodes
- `maxBufferSize`: The max number of streamed block items to buffer before cancelling the block node subscription
- `maxStreamResponseSize`: The max size of a stream response from block nodes
- `maxSubscribeAttempts`: The max number of consecutive subscribe attempts to a block node before marking the node inactive
- `readmitDelay`: The time to wait before readmitting an inactive block node
- `responseTimeout`: The block node server status request timeout

### BlockStream

```java
public record BlockStream(List<BlockItem> blockItems, byte[] bytes, String filename, long loadStart, long nodeId) {}
```

`BlockStream` represents a block from a downloaded block file, or a block streamed from a block node. The `filename`
is the downloaded block file's name, or generated from the block number with `.blk` suffix. `nodeId` indicates which
node the block file is downloaded from, and for block nodes, it's always -1.

### BlockStreamReader

```java
public interface BlockStreamReader {
    BlockFile read(BlockStream blockStream);
}
```

Renamed from `BlockFileReader`. Note the interface no longer extends `StreamFileReader` because it now reads
a `BlockStream` instead of a `StreamFileData`.

### BlockSource

```java

interface BlockSource {
  /*
   * Gets blocks from the source
   */
  void get();
}
```

This replaces interface `StreamPoller` since poll is an incorrect term when the implementation streams data pushed from
server.

### AbstractBlockSource

```java
abstract class AbstractBlockSource implements BlockSource {

    protected final long getNextBlockNumber();

    protected final void onBlockStream(BlockStream blockStream) {
        read block stream;
        verify block stream;
    }
}
```

### BlockFileSource

```java
final class BlockFileSource extends AbstractBlockSource {

    @Override
    public void get() {
        call getNextBlockNumber;
        for each node
            try
                download the block file from the node
                call onBlockStream
                return
            catch error and continue to next node
    }
}
```

`BlockFileSource` polls a block file using `StreamFileProvider`, and calls `AbstractBlockSource.onBlockStream`.

### BlockNodeSubscriber

```java
final class BlockNodeSubscriber extends AbstractBlockSource {

    @Override
    public void get() {
        call getNextBlockNumber;
        call BlockNode.hasBlock to find the first block node with the next block;
        throw error if no block node has the next block;

        start streaming with a backpressure buffer
          for each streamed block
            call onBlockStream;
          error:
            ignore backpressure buffer overflow and throw any other error;
    }
}
```

`BlockNodeSubscriber` streams blocks from a list of block nodes in round-robin starting from the first node in the
configuration. At any time, `BlockNodeSubscriber` should only communicate with one block node. `BlockNodeSubscriber`
should set a timeout while waiting for streamed blocks. Upon any error during streaming and block processing,
`BlockNodeSubscriber` should clean up resources and throw exception so `CompositeBlockStreamSource` can maintain health
status, and have `BlockNodeSubscriber` try the next block node, or switch to `BlockFileSource` if needed.

`BlockNodeSubscriber` should call `BlockNode.hasBlock(blockNumber)` to determine if a block node can provide a block
before start streaming and fail fast if none of the block nodes have the next block.

It's possible that a streaming response from a block node only contains part of a block, `BlockNodeSubscriber` needs to
combine block items in the same block and only then calls `onBlockStream`. Note that a streaming response from the block
node would never have block items from two blocks mixed, i.e., to determine if the block items in the response completes
a block, simply check if the last block item is a `BlockProof`.

### BlockNode

```java
final class BlockNode {

    public boolean hasBlock(long blockNumber);

    public boolean isActive();

    public void streamBlocks(long blockNumber, Duration blockTimeout, Consumer<BlockStream> onBlockStream);

    public BlockNode tryReadmit(boolean force);
}
```

`BlockNode` is the class for all block node communication and state maintenance.

It's possible that a streaming response only contains part of a block, `BlockNode` needs to combine block items in the
same block and only then notify the caller of the completed block. Note that a streaming response would never have block
items from different blocks mixed, i.e., to determine if the block items in the response complete a block, simply check
if the last block item is a `BlockProof`.

### CompositeBlockSource

```java
final class CompositeBlockSource implements BlockSource {

    @Override
    public void get();
}
```

It delegates to either `BlockFileSource` or `BlockNodeSubscriber` to get blocks, depending on the configuration and the
delegates' health.

`AUTO` source type supports automatically switching from cloud storage to block nodes and continuing streaming from
block nodes across importer restarts once the switch happens:

- when there is no block nodes configured, get block files using `BlockFileSource`
- when there is no record file or block file downloaded, use `BlockNodeSubscriber` first
- when one source is unhealthy, e.g., it fails to download one block 5 times consecutively, switch to the other source
- once a block is streamed from a block node and validated, change to always use `BlockNodeSubscriber`, even when
  `BlockNodeSubscriber` fails to stream blocks consistently. This can be determined by checking if the filename of the
  last validated block file ends with `.blk` or not. Note the last validated block file can be either cached in memory
  or read from `record_file` table
