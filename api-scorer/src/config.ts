import { resolve } from 'node:path';

/**
 * Everything the process reads from its environment, in one place. The Helm
 * chart supplies these through a ConfigMap consumed with `envFrom`, the way
 * api-portal's does.
 */
export interface Config {
  readonly host: string;
  readonly port: number;
  readonly logLevel: string;
  /** Base path the contract's `servers[0].url` declares. */
  readonly basePath: string;
  /** The OpenAPI document the routes derive their schemas from. */
  readonly specPath: string;
  /** Which scoring engine to wire in. */
  readonly scorerAdapter: ScorerAdapter;
  /** Rule → dimension mapping and severity overrides. */
  readonly evaluationPath: string;
  /** Ceiling on a fetched specification. */
  readonly specMaxBytes: number;
  readonly specFetchTimeoutMs: number;
  /** Ceiling on one linting run. */
  readonly scoringTimeoutMs: number;
  readonly maxViolations: number;
}

export const SCORER_ADAPTERS = ['dummy', 'spectral'] as const;
export type ScorerAdapter = (typeof SCORER_ADAPTERS)[number];

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  return {
    // 0.0.0.0 so the kubelet can reach the probes.
    host: env['HOST'] ?? '0.0.0.0',
    port: number(env['PORT'], 3000),
    logLevel: env['LOG_LEVEL'] ?? 'info',
    basePath: env['API_BASE_PATH'] ?? '/api/v1',
    specPath: resolve(env['SPEC_PATH'] ?? 'api/scoring/scoring_v1.openapi.yaml'),
    // Defaults to the dummy engine so the module is deployable standalone.
    scorerAdapter: scorerAdapter(env['SCORER_ADAPTER']),
    evaluationPath: resolve(env['SPECTRAL_EVALUATION'] ?? 'rulesets/evaluation.yaml'),
    specMaxBytes: number(env['SPEC_MAX_BYTES'], 5 * 1024 * 1024),
    specFetchTimeoutMs: number(env['SPEC_FETCH_TIMEOUT_MS'], 10_000),
    scoringTimeoutMs: number(env['SCORING_TIMEOUT_MS'], 30_000),
    maxViolations: number(env['MAX_VIOLATIONS'], 100),
  };
}

/** A typo here would silently fall back to the dummy engine — fail loudly instead. */
function scorerAdapter(value: string | undefined): ScorerAdapter {
  if (value === undefined || value === '') return 'dummy';

  const known = SCORER_ADAPTERS.find((adapter) => adapter === value);
  if (!known) {
    throw new Error(
      `SCORER_ADAPTER must be one of ${SCORER_ADAPTERS.join(', ')}, but was "${value}".`,
    );
  }
  return known;
}

function number(value: string | undefined, fallback: number): number {
  if (value === undefined || value === '') return fallback;

  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`Expected a number but got "${value}".`);
  }
  return parsed;
}
