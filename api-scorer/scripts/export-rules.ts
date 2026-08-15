/**
 * Dumps the scorer's *effective* ruleset as JSON, for the portal to document.
 *
 * The rules that actually grade a specification exist only after composition:
 * `spectral:oas` + the OWASP ruleset (or `spectral:asyncapi`) are merged, then
 * `rulesets/evaluation.yaml` overrides severities and stamps each rule with its
 * dimension. Neither file on its own lists what runs — so the documentation
 * cannot be transcribed from a ruleset, it has to be exported from the same
 * composition the service uses at boot.
 *
 * That is why this reuses `compileRulesets` rather than re-reading the YAML:
 * a second implementation of the glob matching would drift from the real one
 * and document rules under the wrong dimension.
 *
 * The scoring *policy* — what a broken rule costs, how dimensions are weighted,
 * where the grade bands fall — lives in `src/domain/scoring.ts`, so it is read
 * from there too. Grade bands are recovered by probing `gradeOf`, which keeps
 * the export honest if the curve is retuned.
 *
 * Usage:
 *   npm run export:rules -- [outfile]
 * Default outfile: ../api-portal/src/data/scorer-rules.json
 */

import { writeFile, mkdir, readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compileRulesets } from '#adapter/out/spectral/RulesetFactory.js';
import { toFindingSeverity } from '#adapter/out/spectral/severity.js';
import { DIMENSION_INTENTS, DIMENSION_NAMES, gradeOf, type Grade } from '#domain/model.js';
import { DEFAULT_PENALTIES, DEFAULT_WEIGHTS } from '#domain/scoring.js';

const moduleDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(moduleDir, '..');

const evaluationPath = resolve(projectRoot, 'rulesets/evaluation.yaml');
const outPath = resolve(
  projectRoot,
  process.argv[2] ?? '../api-portal/src/data/scorer-rules.json',
);

/**
 * Package version as installed, so the export says what it was built from.
 * Read off disk rather than `require`d: the OWASP ruleset does not list
 * `./package.json` in its `exports`, so resolving it as a subpath fails.
 */
async function versionOf(pkg: string): Promise<string> {
  const manifest = resolve(projectRoot, 'node_modules', pkg, 'package.json');
  const { version } = JSON.parse(await readFile(manifest, 'utf8')) as { version: string };
  return version;
}

/** Recover the grade bands from the policy function rather than restating them. */
function gradeBands(): { grade: Grade; min: number }[] {
  const bands: { grade: Grade; min: number }[] = [];
  for (let score = 100; score >= 0; score--) {
    const grade = gradeOf(score);
    const current = bands.at(-1);
    if (current?.grade === grade) current.min = score;
    else bands.push({ grade, min: score });
  }
  return bands;
}

const compiled = await compileRulesets(evaluationPath);

const contracts = Object.fromEntries(
  [...compiled].map(([contractType, { ruleset, coverage }]) => [
    contractType,
    {
      coverage,
      rules: Object.entries(ruleset.rules)
        .map(([name, rule]) => ({
          name,
          dimension: rule.extensions?.['dimension'],
          severity: toFindingSeverity(rule.severity as number),
          enabled: rule.enabled,
          description: rule.description ?? undefined,
          documentationUrl: rule.documentationUrl ?? undefined,
        }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    },
  ]),
);

const payload = {
  // No timestamp on purpose: a regenerated export should be an empty diff when
  // nothing changed, so it can be checked and reviewed like any other source.
  source: {
    evaluation: 'api-scorer/rulesets/evaluation.yaml',
    rulesets: {
      '@stoplight/spectral-rulesets': await versionOf('@stoplight/spectral-rulesets'),
      '@stoplight/spectral-owasp-ruleset': await versionOf('@stoplight/spectral-owasp-ruleset'),
    },
  },
  policy: {
    penalties: DEFAULT_PENALTIES,
    weights: DEFAULT_WEIGHTS,
    grades: gradeBands(),
  },
  dimensions: Object.fromEntries(
    DIMENSION_NAMES.map((name) => [name, { intent: DIMENSION_INTENTS[name] }]),
  ),
  contracts,
};

await mkdir(dirname(outPath), { recursive: true });
await writeFile(outPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');

const counts = Object.entries(contracts)
  .map(([type, { rules }]) => `${type}: ${rules.length}`)
  .join(', ');
console.log(`Exported ${counts} → ${outPath}`);
