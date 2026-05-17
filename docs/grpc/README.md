# gRPC API

The gRPC API provides a protobuf defined interface for interacting with the mirror node.

## Consensus Service

### Subscribe Topic

The Hedera Consensus Service (HCS) provides decentralized consensus on the validity and order of messages submitted to a
topic on the network and transparency into the history of these events over time. The `subscribeTopic` API allows a
client to subscribe to a topic and stream messages asynchronously as they arrive at the mirror node. See the protobuf
[definition](../../protobuf/src/main/proto/com/hedera/mirror/api/proto/consensus_service.proto).

Example invocation using [grpcurl](https://github.com/fullstorydev/grpcurl):

`grpcurl -plaintext -d '{"topicID": {"topicNum": 41110}, "limit": 0}' localhost:5600 com.hedera.mirror.api.proto.ConsensusService/subscribeTopic`

## Network Service

### Get Nodes

[HIP-21](https://hips.hedera.com/hip/hip-21) describes a need for clients to retrieve address book information without
incurring the costs of multiple queries to get the network file's contents. The `getNode` API will return the list of
nodes associated with the latest address book file. See the protobuf
[definition](../../protobuf/src/main/proto/com/hedera/mirror/api/proto/network_service.proto).

Example invocation using `grpcurl`:

`grpcurl -plaintext -d '{"file_id": {"fileNum": 102}, "limit": 0}' localhost:5600 com.hedera.mirror.api.proto.NetworkService/getNodes`
