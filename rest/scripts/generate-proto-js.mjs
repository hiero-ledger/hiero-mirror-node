// SPDX-License-Identifier: Apache-2.0

/**
 * Downloads HAPI protos into rest/proto/services/ from hiero-consensus-node at the tag
 * v{consensusNodeVersion} from root build.gradle.kts, writes that git tag to rest/proto/version.txt,
 * then runs `buf generate` (output in rest/gen/**). If rest/proto/version.txt already contains that
 * same tag, download and buf generate are skipped.
 *
 * Google well-known protos (e.g. google/protobuf/wrappers.proto) are expected to exist in the repo
 * under rest/proto/google/ (not downloaded here).
 *
 * Run with `npm run generate-proto-js`
 */

import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

/** Same set as rest/proto/services (mirror REST vendored HAPI protos). */
const PROTO_SERVICES_FILES = [
  'basic_types.proto',
  'contract_types.proto',
  'custom_fees.proto',
  'exchange_rate.proto',
  'response_code.proto',
  'timestamp.proto',
];

const CONSENSUS_NODE_REPO = 'hiero-ledger/hiero-consensus-node';
const UPSTREAM_SERVICES_PREFIX = 'hapi/hedera-protobuf-java-api/src/main/proto/services';

const restDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = path.resolve(restDir, '..');
const protoVersionFile = path.join(restDir, 'proto', 'version.txt');

function parseConsensusNodeVersion() {
  const gradlePath = path.join(repoRoot, 'build.gradle.kts');
  const text = fs.readFileSync(gradlePath, 'utf8');
  const m = text.match(/set\("consensusNodeVersion",\s*"([^"]+)"\)/);
  if (!m) {
    throw new Error(`Could not find consensusNodeVersion in ${gradlePath}`);
  }
  return m[1];
}

function consensusGitTag() {
  const version = parseConsensusNodeVersion();
  return version.startsWith('v') ? version : `v${version}`;
}

function readStoredConsensusTag() {
  if (!fs.existsSync(protoVersionFile)) {
    return null;
  }
  const line = fs.readFileSync(protoVersionFile, 'utf8').trim().split(/\r?\n/)[0];
  return line?.trim() || null;
}

function normalizeProtoText(s) {
  return s.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
}

async function fetchText(url) {
  const res = await fetch(url, {signal: AbortSignal.timeout(120_000)});
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} for ${url}`);
  }
  return res.text();
}

function consensusServicesRawUrl(tag, filename) {
  return `https://raw.githubusercontent.com/${CONSENSUS_NODE_REPO}/${tag}/${UPSTREAM_SERVICES_PREFIX}/${filename}`;
}

async function downloadProtosFromConsensus(consensusTag) {
  const protoDir = path.join(restDir, 'proto');
  const servicesDir = path.join(protoDir, 'services');

  fs.mkdirSync(servicesDir, {recursive: true});

  fs.writeFileSync(protoVersionFile, `${consensusTag}\n`, 'utf8');

  console.log(
    `Downloading services/*.proto from ${CONSENSUS_NODE_REPO} @ ${consensusTag}`,
  );

  for (const file of PROTO_SERVICES_FILES) {
    const url = consensusServicesRawUrl(consensusTag, file);
    const text = normalizeProtoText(await fetchText(url));
    fs.writeFileSync(path.join(servicesDir, file), text, 'utf8');
    console.log(`Downloaded proto/services/${file}`);
  }
}

function runBufGenerate() {
  const bufBin = path.join(
    restDir,
    'node_modules',
    '.bin',
    process.platform === 'win32' ? 'buf.cmd' : 'buf',
  );

  const result = spawnSync(bufBin, ['generate'], {
    cwd: restDir,
    stdio: 'inherit',
  });

  if (result.error) {
    console.error(result.error);
    return 1;
  }
  return result.status === null ? 1 : result.status;
}

const expectedTag = consensusGitTag();
const storedTag = readStoredConsensusTag();

if (storedTag === expectedTag) {
  console.log(
    `Proto sources and codegen are up to date for ${expectedTag} (rest/proto/version.txt). Skipping download and buf generate.`,
  );
  process.exit(0);
}

await downloadProtosFromConsensus(expectedTag);
process.exit(runBufGenerate());
