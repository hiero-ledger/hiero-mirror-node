// SPDX-License-Identifier: Apache-2.0

import {build} from 'esbuild';
import {cpSync} from 'fs';

await build({
  entryPoints: ['server.js'],
  bundle: true,
  platform: 'node',
  target: 'node24',
  format: 'esm',
  outdir: 'dist',
  splitting: true, // preserves dynamic imports as separate chunks
  external: [
    './__tests__/*', // test-only dynamic import in utils.js
    'log4js', // uses dynamic require(moduleName) for appender loading
    'swagger-stats', // serves static files from its own node_modules directory
    'swagger-ui-express', // serves static files from its own node_modules directory
  ],
  banner: {
    // The SPDX header is required by license policy.
    // The createRequire line fixes CJS packages that call require() for Node.js built-ins
    // (e.g. express-http-context requires 'async_hooks') inside an ESM bundle.
    js: "// SPDX-License-Identifier: Apache-2.0\nimport {createRequire} from 'module';\nconst require = createRequire(import.meta.url);",
  },
  sourcemap: true, // maps bundle stack traces back to original source files
  logLevel: 'info',
});

// Copy static assets into dist so the bundled app can locate them at runtime.
// config.js uses import.meta.url to resolve the default config directory, which
// points to dist/ after bundling, so config/ must live at dist/config/.
cpSync('config', 'dist/config', {recursive: true});

// Generate a minimal package.json for the runtime Docker image that only lists
// the packages kept external from the bundle. This allows the Dockerfile runtime
// stage to run `npm ci` with a much smaller dependency tree instead of installing
// all 48 production dependencies.
import {readFileSync, writeFileSync} from 'fs';
const {name, version, dependencies} = JSON.parse(readFileSync('package.json', 'utf8'));
const externalPackages = ['log4js', 'swagger-stats', 'swagger-ui-express'];
const runtimePackage = {
  name,
  version,
  private: true,
  type: 'module',
  dependencies: Object.fromEntries(externalPackages.map((pkg) => [pkg, dependencies[pkg]])),
};
writeFileSync('dist/package.json', JSON.stringify(runtimePackage, null, 2));
