import {dirname, join, resolve as pathResolve} from 'path';
import {fileURLToPath, pathToFileURL} from 'url';
import {existsSync, statSync} from 'fs';

export async function resolve(specifier, context, defaultResolve) {
  try {
    return await defaultResolve(specifier, context, defaultResolve);
  } catch (e) {
    const parentPath = fileURLToPath(context.parentURL);
    let resolvedPath = pathResolve(dirname(parentPath), specifier);

    // Try resolving directory to index.js
    if (existsSync(resolvedPath) && statSync(resolvedPath).isDirectory()) {
      const indexPath = join(resolvedPath, 'index.js');
      if (existsSync(indexPath)) {
        return {
          url: pathToFileURL(indexPath).href,
        };
      }
    }

    // Try appending `.js`
    if (!specifier.endsWith('.js')) {
      const withJs = resolvedPath + '.js';
      if (existsSync(withJs)) {
        return {
          url: pathToFileURL(withJs).href,
        };
      }
    }

    throw e;
  }
}