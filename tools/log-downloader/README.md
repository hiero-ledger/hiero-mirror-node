# REST API Traffic Replay

This documents describes how to use the tools and [goreplay](https://github.com/buger/goreplay) to replay REST API
traffic to a target server of choice.

## Prerequisites

- gcloud - follow the official [installation guide](https://cloud.google.com/sdk/docs/install) to install the gcloud
  command line tool
- goreplay - either download the v1.3.3 binary from the official [release](https://github.com/buger/goreplay/releases/tag/1.3.3)
  or clone the source and build it.
- nodejs >= 18
- read access to the google cloud logging service in the project which hosts the GKE cluster with mirrornode REST
  service

## Preparation

The `log-downloader` tool requires google cloud Application Default Credentials (ADC) to authenticate itself to call
Google APIs. To use your own user credentials, run

```bash
gcloud auth application-default login
```

and follow the prompts to approve and save the credentials to the designated location.

## Replay traffic

There are two simple steps. And step 2 can be repeated to replay the traffic with existing log files in goreplay format.

1. Download and convert the logs using `log-downloader`. The tool has pre-configured logging filter support for
   `mainnet-na`, `mainnet-eu`, and `testnet-eu`. It requires the user to provide a log start date and optional duration
   (default to 10s). The logs will be converted and saved to a disk file. Example usage:

   ```bash
   $ node log-downloader/index.js --filter $CLUSTER_FILTER -f 2024-10-05T00:00:00Z -d 10m -o demo-2024-10-05-gor.log -p demo-project
   ```

   Please refer to internal documentation for the available cluster filters.

2. Replay the traffic using goreplay. Example usage:

   ```bash
   $ gor --input-file demo-2024-10-05-gor.log --output-http http://localhost:8080
   ```

   Use `--stats --output-http-stats` to show the statistics every 5 seconds. The interval can be changed with
   `--output-http-stats-ms`.

   Additional frequently used options:

   - `--input-file-loop` to loop the input(s) indefinitely
   - Increase or decrease rate: add a percentage limiter after the replay filename, e.g., `"traffic.log|10%"` or
     `"traffic.log|200%"`

   Please refer to the [Wiki](https://github.com/buger/goreplay/wiki) for more information.
