# Hedera Mirror State Proof Alpha

The Mirror Node State Proof Alpha provides the ability to cryptographically prove a transaction is valid on Hedera Network.
It's the interim solution until [full state proof](https://www.hedera.com/blog/state-proofs-on-hedera) is implemented.

Refer to the [State Proof Alpha Design](/docs/design/stateproofalpha.md) for more architectural details.

A node based CLI tool `check-state-proof` is provided here to showcase the steps necessary to independently check the validity of a transaction.

## Logic

The CLI takes the following steps to prove legitimacy of provided transaction ID

1. Obtain user input of transactionId and other params

2. Make a REST API call to the mirror node to retrieve stateproof supporting files/objects - addressBook(s), signature files and a record file.

3. Store files locally and verify at least 1 addressBook, 2 signatures, 1 record file were retrieved.

4. Parse AddressBook(s) to pull out and create a map of nodeIds to public keys

5. Parse signature file buffer to pull out signature and hash pairs from each file

6. Parse record file to pull out file hash or metadata hash and a map of transactionsIds

7. Verify the record file contains the requested transactionId

8. For record file v5, verify that `hash(hash in the start running hash object | hashes_before | hash of the
record stream object | hashes_after)` matches the `hash in the end running hash object`

9. Verify the signatures of file hash or metadata hash from the signature files

10. Verify there is a hash from the signatures files that is matched by at least 1/3 of the nodes.

11. Verify the record file hash or metadata hash matches the hash that reached 1/3 consensus from nodes.

12. Return true if all verifications pass

## Requirements

To run the CLI you must

1. Install the node application
2. Point to a mirror node with the State Proof Alpha REST API enabled.

If you would like to configure your mirror node to support State Proof Alpha you can follow the configuration steps [Enable State Proof Alpha](/docs/configuration.md#enable-state-proof-alpha)

### Install CLI

The node based CLI tool `check-state-proof` can be installed as follows

1. Ensure you've installed [NodeJS](https://nodejs.org/en/about/)
2. Navigate to the `hedera-mirror-rest/check-state-proof` directory
3. Npm install the tool - `npm install -g .`

To verify correct installation simply run `check-state-proof --help` or `npm start -- --help` to show usage instructions.

## Run Check-State-Proof CLI

From command line run

`check-state-proof -t <transactionId> -h <host> -e <environment> -f <filePath>`

Usage options include

```.env
      --help           Show help                                       [boolean]
      --version        Show version number                             [boolean]
  -t, --transactionId  Your Hedera Network Transaction Id e.g.
                       0.0.94139-1570800748-313194300        [string] [required]
  -f, --file           Absolute file path containing State Proof REST API
                       response json                                    [string]
  -h, --host           REST API host. Default is testnet. This overrides the
                       value of the env if also provided.               [string]
  -e, --env            Your environment e.g. local|mainnet|previewnet|testnet
                                                                        [string]
  -n, --nonce          The transaction ID nonce            [number] [default: 0]
  -s, --scheduled      Whether the transaction is scheduled or not     [boolean]
```

> **_Note 1:_** The host value overrides the value of the environment.

> **_Note 2:_** `npm start --` may be used in favor of `check-state-proof` if you don't install the tool globally
> e.g. `npm start -- -t <transactionId> -h <host> -e <previewnet|mainnet|testnet> -f <filePath>`

### Sample Case

To verify the sample case run the following command

`check-state-proof -t 0.0.94139-1570800748-313194300 -f <absoluteDirPath>/stateProofSample.json`

> **_Note_** The -f option requires an absolute filePath to be provided

### Environment

The tool can be run against a public environment as follows

`check-state-proof -t <transactionId> -e <previewnet|mainnet|testnet>`

### Custom Endpoint Case

The tool can be run against a custom endpoint as follows

`check-state-proof -t <transactionId> -h http://127.0.0.1:1234`

or

`check-state-proof -t <transactionId> -h https://my-mirror-node.com`

### Input File

The tool can be run using a provided input file containing the response from te State Proof Alpha API as follows

`check-state-proof -t <transactionId> -f <filePath>`
