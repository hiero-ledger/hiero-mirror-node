// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import log4js from 'log4js';
import fetch from 'node-fetch';
import path from 'path';

const logger = log4js.getLogger();

const makeStateProofDir = (transactionId, stateProofJson) => {
  const outputDir = path.join('output', transactionId);
  fs.mkdirSync(outputDir, {recursive: true});
  fs.writeFileSync(path.join(outputDir, 'apiResponse.json'), JSON.stringify(stateProofJson));
  logger.info(`Supporting files and API response for the state proof will be stored in the directory ${outputDir}`);
  return outputDir;
};

const storeFile = (data, file, ext) => {
  if (!Buffer.isBuffer(data) && typeof data !== 'string') {
    logger.info(`Skip saving file "${file}" since the data is neither a Buffer nor a string`);
    return;
  }

  const filename = `${file}.${ext}`;
  fs.writeFileSync(`${filename}`, data, (err) => {
    if (err) {
      throw err;
    }
  });
};

const getAPIResponse = async (url) => {
  const controller = new AbortController();
  const timeout = setTimeout(
    () => {
      controller.abort();
    },
    60 * 1000 // in ms
  );

  logger.info(`Requesting state proof files from ${url}...`);
  return fetch(url, {signal: controller.signal})
    .then(async (response) => {
      if (!response.ok) {
        throw Error(response.statusText);
      }
      return response.json();
    })
    .catch((error) => {
      throw Error(`Error fetching ${url}: ${error}`);
    })
    .finally(() => {
      clearTimeout(timeout);
    });
};

const readJSONFile = (filePath) => JSON.parse(fs.readFileSync(filePath, 'utf8'));

export {getAPIResponse, makeStateProofDir, readJSONFile, storeFile};
