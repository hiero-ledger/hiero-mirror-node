// SPDX-License-Identifier: Apache-2.0

/**
 * Downloads HAPI protos into rest/proto/services/ from hiero-consensus-node at a git tag derived from:
 * - root build.gradle.kts `consensusNodeVersion` when that file is accessible, or
 * - rest/proto/version.txt when not in build context (e.g. Docker build).
 *
 * Updates rest/proto/version.txt when protos are downloaded, then runs buf generate (rest/gen/**).
 * Skips download and buf when the stored tag matches the expected tag and gen output is present.
 *
 * Google well-known protos (e.g. google/protobuf/wrappers.proto) are expected under rest/proto/google/.
 *
 * Run with `npm run generate-proto-js`
 */

import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

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
const protoGeneratedFile = path.join(restDir, 'gen', 'services', 'basic_types_pb.js');

function normalizeConsensusTag(versionOrTag) {
  if (versionOrTag == null || versionOrTag === '') {
    return null;
  }
  const line = String(versionOrTag).trim().split(/\r?\n/, 1)[0]?.trim() || '';
  if (!line) {
    return null;
  }
  return line.startsWith('v') ? line : `v${line}`;
}

function consensusGitTagFromGradle() {
  const gradlePath = path.join(repoRoot, 'build.gradle.kts');
  const text = fs.readFileSync(gradlePath, 'utf8');
  const m = text.match(/set\("consensusNodeVersion",\s*"([^"]+)"\)/);
  if (!m) {
    throw new Error(`Could not find consensusNodeVersion in ${gradlePath}`);
  }
  return normalizeConsensusTag(m[1]);
}

function readStoredConsensusTag() {
  if (!fs.existsSync(protoVersionFile)) {
    return null;
  }
  const line = fs.readFileSync(protoVersionFile, 'utf8').split(/\r?\n/, 1)[0]?.trim();
  return line || null;
}

function resolveExpectedConsensusTag() {
  const gradlePath = path.join(repoRoot, 'build.gradle.kts');
  if (fs.existsSync(gradlePath)) {
    return consensusGitTagFromGradle();
  }
  const fromFile = readStoredConsensusTag();
  if (!fromFile) {
    throw new Error(
      `Cannot determine consensus node proto version: ${gradlePath} not found and ${protoVersionFile} is missing or empty.`,
    );
  }

  const normalizedConsensusTag = normalizeConsensusTag(fromFile);
  console.log(
    `${gradlePath} not found; using tag from rest/proto/version.txt (${normalizedConsensusTag}).`,
  );
  return normalizedConsensusTag;
}

function isProtoCodegenPresent() {
  return fs.existsSync(protoGeneratedFile);
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
  const servicesDir = path.join(restDir, 'proto', 'services');
  fs.mkdirSync(servicesDir, {recursive: true});
  console.log(
    `Downloading services/*.proto from ${CONSENSUS_NODE_REPO} for tag ${consensusTag}`,
  );

  for (const file of PROTO_SERVICES_FILES) {
    const url = consensusServicesRawUrl(consensusTag, file);
    const text = normalizeProtoText(await fetchText(url));
    fs.writeFileSync(path.join(servicesDir, file), text, 'utf8');
    console.log(`Downloaded proto/services/${file}`);
  }

  fs.writeFileSync(protoVersionFile, `${consensusTag}`, 'utf8');
}

function runBufGenerate() {
  const bufBin = path.join(
    restDir,
    'node_modules',
    '.bin',
    process.platform === 'win32' ? 'buf.cmd' : 'buf',
  );
  const result = spawnSync(bufBin, ['generate'], {cwd: restDir, stdio: 'inherit'});
  if (result.error) {
    console.error(result.error);
    return 1;
  }
  return result.status === null ? 1 : result.status;
}

const expectedTag = resolveExpectedConsensusTag();
const storedNormalized = normalizeConsensusTag(readStoredConsensusTag());

if (storedNormalized === expectedTag && isProtoCodegenPresent()) {
  console.log(
    `Proto sources and codegen are up to date for ${expectedTag} (rest/proto/version.txt). Skipping download and buf generate.`,
  );
  process.exit(0);
}

await downloadProtosFromConsensus(expectedTag);
process.exit(runBufGenerate());
