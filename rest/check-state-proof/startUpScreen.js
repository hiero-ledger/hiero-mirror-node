// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import chalk from 'chalk'; //  pretty up request info
import boxen from 'boxen'; //  emphasize request info
import log4js from 'log4js';
import yargs from 'yargs'; //  simplify user input
import {hideBin} from 'yargs/helpers';

const logger = log4js.getLogger();

const boxenOptions = {
  padding: 1,
  margin: 1,
  borderStyle: 'round',
  borderColor: 'green',
  backgroundColor: '#555555',
};

const options = yargs(hideBin(process.argv))
  .usage('Usage: -t <transactionId> -e <env>')
  .option('transactionId', {
    alias: 't',
    describe: 'Your Hedera Network Transaction Id e.g. 0.0.94139-1570800748-313194300',
    type: 'string',
    demandOption: true,
  })
  .option('file', {
    alias: 'f',
    describe: 'Absolute file path containing State Proof REST API response json',
    type: 'string',
    demandOption: false,
  })
  .option('host', {
    alias: 'h',
    describe: 'REST API host. Default is testnet. This overrides the value of the env if also provided.',
    type: 'string',
    demandOption: false,
  })
  .option('env', {
    alias: 'e',
    describe: 'Your environment e.g. local|mainnet|previewnet|testnet',
    type: 'string',
    demandOption: false,
  })
  .option('nonce', {
    alias: 'n',
    describe: 'The transaction ID nonce',
    type: 'number',
    default: 0,
    demandOption: false,
  })
  .option('scheduled', {
    alias: 's',
    describe: 'Whether the transaction is scheduled or not',
    type: 'boolean',
    demandOption: false,
  }).argv;

const startUpScreen = () => {
  const greeting = chalk.bold(`Hedera Transaction State Proof Checker CLI`);
  const msgBox = boxen(greeting, boxenOptions);
  logger.info(msgBox);

  const {transactionId, nonce, scheduled} = options;
  const storedFile = options.file;
  let source = storedFile; // default source to filePath
  let url;

  // update source creating a url based on env and or host if no filePath provided
  if (_.isUndefined(storedFile)) {
    let host;
    // if host parameter was not passed then set host according to env.
    if (_.isUndefined(options.host)) {
      switch (options.env) {
        case 'local':
          host = 'http://localhost:5551';
          break;
        case 'mainnet':
          host = 'https://mainnet.mirrornode.hedera.com';
          break;
        case 'previewnet':
          host = 'https://previewnet.mirrornode.hedera.com';
          break;
        case 'testnet':
          host = 'https://testnet.mirrornode.hedera.com';
          break;
        default:
          throw Error(`Invalid env value provided: ${options.env}. Check --help option to see correct usage`);
      }
    } else {
      host = options.host;
    }

    const param = [(nonce !== 0 && `nonce=${nonce}`) || '', scheduled ? 'scheduled=true' : '']
      .filter((x) => !!x)
      .join('&');
    url = `${host}/api/v1/transactions/${transactionId}/stateproof${(param !== '' && '?' + param) || ''}`;
    source = url;
  }

  logger.info(`Initializing state proof for transaction ID ${transactionId} from source: ${source}`);
  return {transactionId, nonce, scheduled, url, storedFile};
};

export default startUpScreen;
