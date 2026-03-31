// SPDX-License-Identifier: Apache-2.0

import {build} from 'esbuild';
// Generate a minimal package.json for the runtime Docker image that only lists
// the packages kept external from the bundle. This allows the Dockerfile runtime
// stage to run `npm install` with a much smaller dependency tree instead of installing
// all production dependencies.
import {cpSync, readFileSync, writeFileSync} from 'fs';

const externalPackages = ['log4js', 'swagger-ui-express', './__tests__/*'];

await build({
  entryPoints: ['server.js'],
  bundle: true,
  platform: 'node',
  target: 'node24',
  format: 'esm',
  outdir: 'buildDist',
  splitting: true, // preserves dynamic imports as separate chunks
  minify: true, // reduces bundle size ~50%, lowering V8 parse/JIT memory at startup
  external: externalPackages,
  banner: {
    // The SPDX header is required by license policy.
    // The createRequire line fixes CJS packages that call require() for Node.js built-ins
    // (e.g. express-http-context requires 'async_hooks') inside an ESM bundle.
    js: "// SPDX-License-Identifier: Apache-2.0\nimport {createRequire} from 'module';\nconst require = createRequire(import.meta.url);",
  },
  logLevel: 'info',
});

// Copy static assets into buildDist so the bundled app can locate them at runtime.
// config.js uses import.meta.url to resolve the default config directory, which
// points to buildDist/ after bundling, so config/ must live at buildDist/config/.
cpSync('config', 'buildDist/config', {recursive: true});
// openapiHandler.js resolves api/v1/openapi.yml via process.cwd(), so api/ must
// be at the app root at runtime. Copying it into buildDist/ keeps all static assets together.
cpSync('api', 'buildDist/api', {recursive: true});

const {name, version, dependencies} = JSON.parse(readFileSync('package.json', 'utf8'));
const runtimePackage = {
  name,
  version,
  private: true,
  type: 'module',
  dependencies: Object.fromEntries(externalPackages.map((pkg) => [pkg, dependencies[pkg]])),
};
writeFileSync('buildDist/package.json', JSON.stringify(runtimePackage, null, 2));
