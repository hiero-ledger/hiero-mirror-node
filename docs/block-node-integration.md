# Mirror Node Setup with Consensus Node 0.73 and Block Node 0.31

This guide details the configuration required for the Mirror Node to successfully
integrate with Consensus Node v0.73 and Block Node v0.31. This setup utilizes
block stream ingestion, which is the preferred method for data ingestion in
these versions.

## Configuration Overview

To enable block stream ingestion from a Block Node, several keys must be
configured in the `importer` section of your `values.yaml` file. These settings
ensure that the Mirror Node connects to the Block Node's gRPC endpoints instead
of relying solely on cloud storage for record files.

### Required values.yaml Settings

The following configuration should be added to your Helm `values.yaml`:

```yaml
importer:
  config:
    hiero:
      mirror:
        importer:
          block:
            enabled: true
            nodes:
              - host: block-node-1.example.com
                port: 40840
              - host: block-node-2.example.com
                port: 40840
            sourceType: BLOCK_NODE
          downloader:
            record:
              enabled: false
```

## Configuration Properties

- **`hiero.mirror.importer.block.enabled`**
  - **Required**: Yes
  - **Default**: `false`
  - **Description**: Enables the block stream ingestion feature.
- **`hiero.mirror.importer.block.sourceType`**
  - **Required**: Yes
  - **Default**: `AUTO`
  - **Description**: Set to `BLOCK_NODE` to prioritize ingestion from Block Nodes.
- **`hiero.mirror.importer.block.nodes`**
  - **Required**: Yes
  - **Default**: `[]`
  - **Description**: A list of Block Node endpoints to connect to.
- **`hiero.mirror.importer.block.nodes[].host`**
  - **Required**: Yes
  - **Default**: N/A
  - **Description**: The hostname or IP address of the Block Node.
- **`hiero.mirror.importer.block.nodes[].port`**
  - **Required**: No
  - **Default**: `40840`
  - **Description**: The gRPC port of the Block Node.
- **`hiero.mirror.importer.downloader.record.enabled`**
  - **Required**: No
  - **Default**: `true`
  - **Description**: Set to `false` if only using Block Nodes for ingestion.

## Troubleshooting

If the block stream ingestion is not configured correctly, you may encounter the
following symptoms:

### Symptoms

- **Importer Lag**: The Mirror Node falls behind the network consensus because
  it cannot find new record files in cloud storage.
- **Log Errors**: "No block source available" or "Unable to connect to block
  node" in the Importer logs.
- **Empty Blocks**: The database shows no new transactions or blocks despite the
  network being active.

### Common Misconfigurations

- **Incorrect Port**: Ensure the port matches the Block Node's gRPC service port
  (default is `40840`).
- **TLS Issues**: If the Block Node requires TLS, ensure
  `hiero.mirror.importer.block.nodes[].requiresTls` is set to `true`.
- **Network Access**: Verify that the Mirror Node pods have network connectivity
  to the Block Node hosts on the specified ports.
