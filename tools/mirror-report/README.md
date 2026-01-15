# Mirror Node Report Tool

This tool is a CLI tool that queries the REST API for an account and produces a report showing any transfers to or from
the account and its resulting balance.

## Install

First ensure Node and NPM are installed. Following the instructions specific to your operating system.

### MacOS

Open the Terminal app and run the following commands:

```shell
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
nvm install 24
npm install -g @hashgraph/mirror-report
```

### Windows

Download the Node.js 24 prebuilt [installer](https://nodejs.org/dist/v24.11.1/node-v24.11.1-x64.msi) for Windows x64.
Follow the prompts to complete the installation with the default options. Open the Command Prompt application and run
the following:

```shell
npm install -g @hashgraph/mirror-report
```

## Upgrading

To upgrade the version of the report tool, re-run the NPM install command to grab the latest version:

```shell
npm install -g @hashgraph/mirror-report
```

## Running

To generate the report, use the `mirror` command line tool from the installation section.

```shell
Usage: mirror [options] [command]

Options:
  -h, --help        display help for command

Commands:
  report [options]  Generate a report for specific accounts
  help [command]    display help for command
```

Run the `report` sub-command to generate the report data. Pass at least one account you want to include in the report.
By default, it generates a report for the current date. Pass the `--from-date YYYY-MM-DD` and `--to-date YYYY-MM-DD`
arguments to customize the time range the report should cover.

```shell
Usage: mirror report [options]

Generate a report for the given accounts.

Options:
  -a, --account <accountId...>  The accounts to include in the report
  -c, --combined                Whether a single combined report should be generated for all accounts. By default it produces separate reports
  -f, --from-date <YYYY-MM-DD>  The day the report should start (inclusive) (default: today)
  -n, --network <network>       The Hedera network to connect to (choices: "mainnet", "testnet", "previewnet", default: "mainnet")
  -t, --to-date <YYYY-MM-DD>    The day the report should end (exclusive) (default: tomorrow)
  -h, --help                    display help for command
```

Example execution:

```shell
mirror report --combined -a 0.0.1000-0.0.1001 0.0.1003 -f 2024-11-29 -n testnet
```

```shell
2025-01-02T19:06:08.399Z Running report with options: {"fromDate":"2024-11-29","network":"testnet","toDate":"2025-01-03","combined":true,"account":["0.0.1000","0.0.1001","0.0.1003"]}
2025-01-02T19:06:08.399Z Invoking https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1732838400
2025-01-02T19:06:08.515Z Starting balance of 1300000002 for account 0.0.1000 at 1732837599.823821194
2025-01-02T19:06:08.515Z Invoking https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1000&limit=100&order=asc&timestamp=gt:1732837599.823821194&timestamp=lt:1735862400
2025-01-02T19:06:08.591Z Invoking https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1001?timestamp=1732838400
2025-01-02T19:06:08.639Z Starting balance of 197103815708295 for account 0.0.1001 at 1732837599.823821194
2025-01-02T19:06:08.639Z Invoking https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1001&limit=100&order=asc&timestamp=gt:1732837599.823821194&timestamp=lt:1735862400
2025-01-02T19:06:08.687Z Invoking https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1003?timestamp=1732838400
2025-01-02T19:06:08.737Z Starting balance of 1001658807600 for account 0.0.1003 at 1732837599.823821194
2025-01-02T19:06:08.737Z Invoking https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1003&limit=100&order=asc&timestamp=gt:1732837599.823821194&timestamp=lt:1735862400
2025-01-02T19:06:08.781Z Generated report successfully at report-2024-11-29.csv with 1 entries
```

```shell
cat report-2024-11-29.csv
```

```shell
timestamp,sender,receiver,fees,amount,balance
1732901875.430169000,0.0.5190744,0.0.1000,0.00000000,1.00000000,1.400000002
```
