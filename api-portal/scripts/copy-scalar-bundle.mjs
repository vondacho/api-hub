/*
 * Copy Scalar's standalone browser bundle into public/ so the portal serves it
 * from its own origin.
 *
 * <ScalarComponent> loads the bundle by injecting a <script src>, and defaults
 * that src to https://cdn.jsdelivr.net/npm/@scalar/api-reference. The portal is
 * deployed inside a cluster and deliberately makes no calls to the public
 * internet from the browser, so the contract viewer would go blank wherever
 * jsDelivr is unreachable. Pointing `cdn` at this copy keeps the component and
 * drops the external dependency.
 *
 * The bundle is a build artefact, not source: it is git-ignored and re-copied by
 * prebuild/predev, so it tracks whatever @scalar/api-reference is installed.
 */
import { copyFile, mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const root = join(dirname(fileURLToPath(import.meta.url)), '..');

/* Resolved through the package rather than hardcoding node_modules/, so a
 * hoisted or pnpm-style tree still finds it. Resolution goes via the main entry
 * (dist/index.js) because the package's "exports" map does not expose
 * package.json, nor the bundle itself, to a direct resolve. */
const source = join(dirname(require.resolve('@scalar/api-reference')), 'browser/standalone.js');
const target = join(root, 'public/scalar/standalone.js');

await mkdir(dirname(target), { recursive: true });
await copyFile(source, target);

console.log(`[scalar] ${source} → public/scalar/standalone.js`);
