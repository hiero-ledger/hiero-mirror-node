// SPDX-License-Identifier: Apache-2.0

import assert from 'assert';

// Credits to @awaitjs/express
// The function extends an express app / router and provides `getExt` and `useExt` so the handler / middleware doesn't
// have to call next(). `getExt` and `useExt` only need to be used for our handlers and middlewares.
const extendExpress = (app) => {
  const methods = ['get', 'use'];
  for (const method of methods) {
    app[`${method}Ext`] = function () {
      const fn = arguments[arguments.length - 1];
      assert.ok(typeof fn === 'function', `Last argument to "${method}" must be a function`);
      const args = wrapArgs(arguments);
      return app[method].apply(app, args);
    };
  }

  return app;
};

const wrapArgs = (fns) => {
  const ret = [];
  for (const fn of fns) {
    if (typeof fn !== 'function') {
      ret.push(fn);
      continue;
    }
    ret.push(wrap(fn));
  }

  return ret;
};

const wrap = (fn) => {
  const isErrorHandler = fn.length === 4;
  const wrapped = async function () {
    // Ensure next function is only ran once
    arguments[2 + isErrorHandler] = _once(arguments[2 + isErrorHandler]);
    try {
      const promise = fn.apply(null, arguments);
      if (promise && typeof promise.then === 'function') {
        await promise;
        arguments[1 + isErrorHandler].headersSent ? null : arguments[2 + isErrorHandler]();
      }
    } catch (err) {
      arguments[1 + isErrorHandler].headersSent ? null : arguments[2 + isErrorHandler](err);
    }
  };

  Object.defineProperty(wrapped, 'length', {
    // Length has to be set for express to recognize error handlers as error handlers
    value: isErrorHandler ? 4 : 3,
  });

  Object.defineProperty(wrapped, 'name', {
    // Define a name for stack traces
    value: isErrorHandler ? 'wrappedErrorHandler' : 'wrappedMiddleware',
  });

  return wrapped;
};

const _once = (fn) => {
  let called = false;
  return function () {
    if (called) {
      return;
    }
    called = true;
    fn.apply(null, arguments);
  };
};

export default extendExpress;
