import {
  DIMENSION_INTENTS,
  gradeOf,
  type Dimension,
  type DimensionName,
  type Finding,
  type FindingSeverity,
  type Scorecard,
  type Severity,
  type Violation,
} from './model.js';

/**
 * Turning findings into a scorecard. Pure policy — no I/O, no engine types.
 *
 * The split of responsibility with the scoring engine is deliberate: the engine
 * decides *what fired and how badly* (its ruleset owns each rule's severity and
 * dimension), while this module decides *what that is worth*. Retuning a rule is
 * therefore a ruleset edit, not a code change; retuning the grading curve is a
 * code change here and nowhere else.
 *
 * ## What a dimension score means
 *
 * `100 × (1 − damage / capacity)`: the share of the dimension's rules the
 * specification satisfies, weighted by how serious each rule is.
 *
 * Two properties of that formula are deliberate, because the obvious
 * alternative — start at 100 and subtract per finding — measured the wrong
 * thing:
 *
 * - **Each broken rule counts once, however often it fires.** Otherwise one
 *   chatty rule decides the dimension: `oas3-parameter-description` alone fires
 *   hundreds of times on a large document and would floor the score while every
 *   other rule went unheard.
 * - **The denominator is the ruleset, not a constant.** Subtracting from a fixed
 *   100 punishes documents for being detailed: rules like
 *   `owasp:api4:2023-string-limit` can only fire on a specification that
 *   actually declares strings, so a document that defines nothing would outscore
 *   a thorough one. Measured as a proportion of the rules in play, describing
 *   more of your API can no longer make your score worse by itself.
 */

/** How serious one broken rule is, by the severity its ruleset gives it. */
export type Penalties = Readonly<Record<FindingSeverity, number>>;

export const DEFAULT_PENALTIES: Penalties = {
  ERROR: 10,
  WARNING: 4,
  INFO: 1,
  HINT: 0.5,
};

/** How much each dimension contributes to the global score. */
export type DimensionWeights = Readonly<Partial<Record<DimensionName, number>>>;

export const DEFAULT_WEIGHTS: DimensionWeights = {
  FC: 0.4,
  SEC: 0.3,
  DX: 0.2,
  MR: 0.1,
};

/**
 * What the engine could have checked for one dimension: its enabled rules,
 * counted by severity.
 *
 * Reported by the engine rather than assumed, because "nothing was wrong" and
 * "nothing was checked" must not look alike — a dimension with no rules behind
 * it would otherwise score a flawless 100.
 */
export interface DimensionCoverage {
  readonly dimension: DimensionName;
  readonly enabled: Readonly<Partial<Record<FindingSeverity, number>>>;
}

export interface Assembly {
  readonly findings: readonly Finding[];
  readonly coverage: readonly DimensionCoverage[];
  readonly penalties?: Penalties;
  readonly weights?: DimensionWeights;
  /** Cap on returned violations; the rest are dropped, most serious kept. */
  readonly maxViolations?: number;
}

export function assemble(assembly: Assembly): Scorecard {
  const {
    findings,
    coverage,
    penalties = DEFAULT_PENALTIES,
    weights = DEFAULT_WEIGHTS,
    maxViolations = 100,
  } = assembly;

  const dimensions = coverage
    .map((entry) => scoreDimension(entry, findings, penalties))
    // A dimension with no rules behind it was not measured, so it is not
    // reported. Claiming 100 for it would be a lie by omission.
    .filter((dimension): dimension is Dimension => dimension !== undefined);

  const score = globalScore(dimensions, weights);

  return {
    score,
    grade: gradeOf(score),
    dimensions,
    violations: toViolations(findings, maxViolations),
  };
}

function scoreDimension(
  coverage: DimensionCoverage,
  findings: readonly Finding[],
  penalties: Penalties,
): Dimension | undefined {
  const capacity = Object.entries(coverage.enabled).reduce(
    (total, [severity, count]) => total + penalties[severity as FindingSeverity] * (count ?? 0),
    0,
  );
  if (capacity === 0) return undefined;

  // Distinct rules, not occurrences: breadth of breakage, not volume of output.
  const broken = new Map<string, FindingSeverity>();
  for (const finding of findings) {
    if (finding.dimension === coverage.dimension) broken.set(finding.rule, finding.severity);
  }

  const damage = [...broken.values()].reduce((total, severity) => total + penalties[severity], 0);

  const score = clamp(Math.round(100 * (1 - damage / capacity)));
  return {
    name: coverage.dimension,
    intent: DIMENSION_INTENTS[coverage.dimension],
    score,
    grade: gradeOf(score),
  };
}

/**
 * Weighted mean over the measured dimensions only, renormalised so that leaving
 * a dimension unmeasured neither inflates nor deflates the result.
 */
function globalScore(dimensions: readonly Dimension[], weights: DimensionWeights): number {
  if (dimensions.length === 0) return 0;

  let weighted = 0;
  let total = 0;
  for (const dimension of dimensions) {
    const weight = weights[dimension.name] ?? 0;
    weighted += dimension.score * weight;
    total += weight;
  }

  // Every measured dimension carrying zero weight would otherwise divide by
  // zero; fall back to an unweighted mean rather than inventing a number.
  if (total === 0) {
    return clamp(Math.round(dimensions.reduce((sum, d) => sum + d.score, 0) / dimensions.length));
  }
  return clamp(Math.round(weighted / total));
}

/** Most serious first, so a truncated list keeps what matters. */
function toViolations(findings: readonly Finding[], max: number): readonly Violation[] {
  return [...findings]
    .sort((a, b) => rank(a.severity) - rank(b.severity))
    .slice(0, max)
    .map((finding) => ({
      code: finding.rule,
      detail: finding.location ? `${finding.message} (at ${finding.location})` : finding.message,
      severity: toSeverity(finding.severity),
    }));
}

/**
 * The contract offers two severities where the engine has four. Anything the
 * engine treats as a breach is MAJOR; advisory output is MINOR.
 */
function toSeverity(severity: FindingSeverity): Severity {
  return severity === 'ERROR' || severity === 'WARNING' ? 'MAJOR' : 'MINOR';
}

function rank(severity: FindingSeverity): number {
  return { ERROR: 0, WARNING: 1, INFO: 2, HINT: 3 }[severity];
}

function clamp(score: number): number {
  return Math.max(0, Math.min(100, score));
}
