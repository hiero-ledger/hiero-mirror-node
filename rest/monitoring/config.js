// SPDX-License-Identifier: Apache-2.0

import extend from 'extend';
import fs from 'fs';
import _ from 'lodash';
import logger from './logger';
import path from 'path';
import {fileURLToPath} from 'url';

const REQUIRED_FIELDS = [
  'servers',
  'interval',
  'shard',
  'timeout',
  'account.intervalMultiplier',
  'balance.freshnessThreshold',
  'balance.intervalMultiplier',
  'block.freshnessThreshold',
  'block.intervalMultiplier',
  'network.intervalMultiplier',
  'transaction.freshnessThreshold',
  'transaction.intervalMultiplier',
  'topic.freshnessThreshold',
  'topic.intervalMultiplier',
];

const load = (configFile) => {
  try {
    if (!configFile) {
      return {};
    }

    const data = JSON.parse(fs.readFileSync(configFile).toString('utf8'));
    logger.info(`Loaded configuration source: ${configFile}`);
    return data;
  } catch (err) {
    logger.warn(`Skipping configuration source ${configFile}: ${err}`);
    return {};
  }
};

let config = {};
let loaded = false;

if (!loaded) {
  const moduleDirname = path.dirname(fileURLToPath(import.meta.url));
  config = load(path.join(moduleDirname, 'config', 'default.serverlist.json'));
  const customConfig = load(path.join(moduleDirname, 'config', 'serverlist.json'));
  extend(true, config, customConfig);

  if (process.env.CONFIG_PATH) {
    const customPathConfig = load(path.join(process.env.CONFIG_PATH, 'serverlist.json'));
    extend(true, config, customPathConfig);
  }

  for (const field of REQUIRED_FIELDS) {
    if (!_.has(config, field)) {
      throw new Error(`required field "${field}" not found in any configuration file`);
    }
  }

  if (!Array.isArray(config.servers) || config.servers.length === 0) {
    throw new Error(`Invalid servers "${JSON.stringify(config.servers)}" in any configuration file`);
  }

  logger.info(`Loaded configuration: ${JSON.stringify(config)}`);
  loaded = true;
}

export default config;
