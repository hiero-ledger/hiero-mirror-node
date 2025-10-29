// SPDX-License-Identifier: Apache-2.0

import extend from 'extend';
import fs from 'fs';
import yaml from 'js-yaml';
import parseDuration from 'parse-duration';
import path from 'path';
import {fileURLToPath} from 'url';

import {cloudProviders, defaultBucketNames, NANOSECONDS_PER_MILLISECOND, networks} from './constants';
import {InvalidConfigError} from './errors';
import configureLogger from './logger';

configureLogger();

const config = {};
const defaultConfigName = 'application';
const hederaPrefix = 'hedera';
const hieroPrefix = 'hiero';
let loaded = false;

function load(configPath, configName) {
  if (!configPath) {
    return;
  }

  let configFile = path.join(configPath, `${configName}.yml`);
  if (fs.existsSync(configFile)) {
    loadYaml(configFile);
  }

  configFile = path.join(configPath, `${configName}.yaml`);
  if (fs.existsSync(configFile)) {
    loadYaml(configFile);
  }
}

function loadYaml(configFile) {
  try {
    const doc = yaml.load(fs.readFileSync(configFile, 'utf8'));
    logger.info(`Loaded configuration source: ${configFile}`);
    extend(true, config, doc);

    // Migrated deprecated properties
    const hedera = doc[hederaPrefix];
    if (hedera) {
      extend(true, config, {hiero: hedera});
      delete config[hederaPrefix];
      logger.warn(
        `Source contains deprecated '${hederaPrefix}' properties that have been automatically migrated to '${hieroPrefix}''`
      );
    }
  } catch (err) {
    logger.warn(`Skipping configuration ${configFile}: ${err}`);
  }
}

function loadEnvironment() {
  for (const [key, value] of Object.entries(process.env)) {
    setConfigValue(key, value);
  }
}

/*
 * Sets a config property from an environment variable by converting HIERO_MIRROR_REST_FOO_BAR to an object path
 * notation hiero.mirror.rest.foo.bar using a case insensitive search. If more than one property matches with a
 * different case, it will choose the first. Will also convert HEDERA_MIRROR to hiero.mirror properties.
 */
function setConfigValue(propertyPath, value) {
  let current = config;
  const properties = propertyPath.toLowerCase().split('_');

  // Ignore properties that don't start with HEDERA_MIRROR or HIERO_MIRROR
  if (
    properties.length < 3 ||
    (properties[0] !== 'hedera' && properties[0] !== 'hiero') ||
    properties[1] !== 'mirror'
  ) {
    return;
  }

  for (let i = 0; i < properties.length; i += 1) {
    let property = properties[i];
    let found = false;

    for (const [k, v] of Object.entries(current)) {
      if (property === hederaPrefix) {
        property = hieroPrefix;
        logger.warn(
          `Deprecated '${hederaPrefix}' property automatically migrated to '${hieroPrefix}': ${propertyPath}`
        );
      }

      if (property === k.toLowerCase()) {
        if (i < properties.length - 1) {
          current = v;
          found = true;
          break;
        } else {
          current[k] = convertType(value);
          const cleanedValue = property.includes('password') || property.includes('key') ? '******' : value;
          logger.info(`Override config with environment variable ${propertyPath}=${cleanedValue}`);
          return;
        }
      }
    }

    if (!found) {
      return;
    }
  }
}

function convertType(value) {
  if (value !== null && value !== '' && !isNaN(value)) {
    return +value;
  } else if (value === 'true' || value === 'false') {
    return value === 'true';
  }

  return value;
}

function getConfig() {
  return config.hiero?.mirror?.rest;
}

function getMirrorConfig() {
  return config.hiero?.mirror;
}

function getResponseLimit() {
  return getConfig().response.limit;
}

function parseDbPoolConfig() {
  const {pool} = getConfig().db;
  const configKeys = ['connectionTimeout', 'maxConnections', 'statementTimeout'];
  configKeys.forEach((configKey) => {
    const value = pool[configKey];
    const parsed = parseInt(value, 10);
    if (Number.isNaN(parsed) || parsed <= 0) {
      throw new InvalidConfigError(`invalid value set for db.pool.${configKey}: ${value}`);
    }
    pool[configKey] = parsed;
  });
}

function parseStateProofStreamsConfig() {
  const {stateproof} = getConfig();
  if (!stateproof || !stateproof.enabled) {
    return;
  }

  const {streams: streamsConfig} = stateproof;
  if (!Object.values(networks).includes(streamsConfig.network)) {
    throw new InvalidConfigError(`unknown network ${streamsConfig.network}`);
  }

  if (!streamsConfig.bucketName) {
    streamsConfig.bucketName = defaultBucketNames[streamsConfig.network];
  }

  if (!streamsConfig.bucketName) {
    // the default for network 'OTHER' is null, throw err if it's not configured
    throw new InvalidConfigError('stateproof.streams.bucketName must be set');
  }

  if (!Object.values(cloudProviders).includes(streamsConfig.cloudProvider)) {
    throw new InvalidConfigError(`unsupported object storage service provider ${streamsConfig.cloudProvider}`);
  }
}

const parseDurationConfig = (name, value) => {
  const ms = parseDuration(value);
  if (!ms) {
    throw new InvalidConfigError(`invalid ${name} ${value}`);
  }
  return BigInt(ms) * NANOSECONDS_PER_MILLISECOND;
};

const durationQueryConfigKeys = [
  'maxRecordFileCloseInterval',
  'maxScheduledTransactionConsensusTimestampRange',
  'maxTimestampRange',
  'maxTransactionConsensusTimestampRange',
  'maxTransactionsTimestampRange',
  'maxValidStartTimestampDrift',
];

const parseQueryConfig = () => {
  const {query} = getConfig();
  const {precedingTransactionTypes} = query.transactions;
  if (!Array.isArray(precedingTransactionTypes)) {
    throw new InvalidConfigError(
      `Invalid or missing query.transactions.precedingTransactionTypes: ${precedingTransactionTypes}`
    );
  }
  durationQueryConfigKeys.forEach((key) => (query[`${key}Ns`] = parseDurationConfig(`query.${key}`, query[key])));
};

const parseNetworkConfig = () => {
  const currencyFormat = getConfig().network.currencyFormat;
  if (currencyFormat) {
    const validValues = ['BOTH', 'HBARS', 'TINYBARS'];
    if (!validValues.includes(currencyFormat)) {
      throw new InvalidConfigError(`invalid currencyFormat ${currencyFormat}`);
    }
  }
};

const parseCommon = () => {
  const {common} = getMirrorConfig();
  if (common?.shard !== undefined) {
    common.shard = BigInt(common.shard);
  }
  if (common?.realm !== undefined) {
    common.realm = BigInt(common.realm);
  }
};

if (!loaded) {
  const configName = process.env.CONFIG_NAME || defaultConfigName;
  // always load the default configuration
  const moduleDirname = path.dirname(fileURLToPath(import.meta.url));
  load(path.join(moduleDirname, 'config'), defaultConfigName);
  load(moduleDirname, configName);
  load(process.env.CONFIG_PATH, configName);
  loadEnvironment();
  parseDbPoolConfig();
  parseNetworkConfig();
  parseQueryConfig();
  parseStateProofStreamsConfig();
  parseCommon();
  loaded = true;
  configureLogger(getConfig().log.level);
}

export default getConfig();

export {getMirrorConfig, getResponseLimit};
