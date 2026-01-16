// SPDX-License-Identifier: Apache-2.0

import {InvalidArgumentError} from 'commander';

import {MirrorNodeClient} from './client.js';
import {log} from './logger.js';
import {ReportFile} from './reportfile.js';
import {toHbars, toIsoString} from './utils.js';

const feeAccount = /^\d+\.\d+\.(98|800|801|802|[3-9]|[1-3][0-9])$/;

export const report = async (options) => {
  options.account = [...options.account]; // Convert to array so stringify works
  log(`Running report with options: ${JSON.stringify(options)}`);

  const timestampStart = new Date(options.fromDate).getTime() / 1000;
  const timestampEnd = new Date(options.toDate).getTime() / 1000;
  let reportFile = new ReportFile();
  const client = new MirrorNodeClient(options.network);

  if (timestampStart >= timestampEnd) {
    throw new InvalidArgumentError('From date must be before to date');
  }

  for (const account of options.account) {
    const accountResponse = await client.get(`/accounts/${account}?timestamp=${timestampStart}`);

    if (!accountResponse) {
      log(`Unable to find account ${account}`);
      continue;
    }

    let balance = BigInt(accountResponse?.balance?.balance);
    const balanceTimestamp = accountResponse?.balance?.timestamp;
    let next = `/transactions?account.id=${account}&limit=100&order=asc&timestamp=gt:${balanceTimestamp}&timestamp=lt:${timestampEnd}`;
    log(`Starting balance of ${balance} for account ${account} at ${balanceTimestamp}`);

    while (next) {
      const transactionsResponse = await client.get(next);

      if (!transactionsResponse) {
        log(`Unable to find any transactions for account ${account}`);
        break;
      }

      for (const transaction of transactionsResponse.transactions) {
        const payer = transaction.transaction_id.startsWith(account + '-');
        const fees = payer ? BigInt(transaction.charged_tx_fee) : 0n;
        let amount = 0n;
        let other = 'fee accounts';

        transaction?.transfers.forEach((transfer) => {
          if (transfer.account === account) {
            amount = BigInt(transfer.amount);
          } else if (!feeAccount.test(transfer.account)) {
            other = transfer.account;
          }
        });

        balance += amount;
        const consensusTimestampSeconds = transaction.consensus_timestamp.split('.')[0];

        if (consensusTimestampSeconds >= timestampStart) {
          const sender = amount < 0n ? account : other;
          let receiver = amount < 0n ? other : account;
          const dateTime = toIsoString(transaction.consensus_timestamp);
          const hashscan = `https://hashscan.io/${options.network}/transaction/${transaction.consensus_timestamp}`;
          reportFile.append(
            `${dateTime},${sender},${receiver},${toHbars(fees)},${toHbars(amount)},${toHbars(balance)},${hashscan}\n`
          );
        }
      }

      next = transactionsResponse?.links?.next;
    }

    if (!options.combined) {
      reportFile.write(`report-${options.fromDate}-${account}.csv`);
    }
  }

  if (options.combined) {
    reportFile.write(`report-${options.fromDate}.csv`);
  }
};
