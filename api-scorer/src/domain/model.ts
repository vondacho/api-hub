/**
 * The domain model. Pure TypeScript — no Fastify, no awilix, no generated
 * contract types. Mirrors `io.obya.api.onboarding.domain.model` in the Java
 * module: dependencies point inward, so nothing here may import from `adapter/`.
 *
 * These types deliberately do NOT reuse the generated OpenAPI types. The wire
 * contract and the domain are allowed to drift; `adapter/in/web/model/mapper.ts`
 * is the single place that reconciles them.
 */

/** Specification contract types the scorer knows how to grade. */
export const CONTRACT_TYPES = ['OPENAPI', 'ASYNCAPI', 'GRAPHQLS'] as const;
export type ContractType = (typeof CONTRACT_TYPES)[number];

/** Overall quality score, 0–100. */
export type Score = number;

/** Letter grade derived from a {@link Score}. */
export type Grade = 'A+' | 'A' | 'A-' | 'B+' | 'B' | 'B-' | 'C+' | 'C' | 'C-' | 'D+' | 'D' | 'D-';

/** The scoring dimensions a scorecard breaks down into. */
export const DIMENSION_NAMES = ['FC', 'SEC', 'DX', 'MR', 'ARAX', 'AU', 'AUD'] as const;
export type DimensionName = (typeof DIMENSION_NAMES)[number];

/**
 * What each dimension is for. Wording taken from api-onboarding's
 * `Scorecard.Dimension` so both modules describe a dimension the same way.
 *
 * Only ARAX, AU and AUD have no rules behind them today: Spectral cannot
 * measure them, so they are never emitted. They stay listed here because the
 * contract enumerates them and the domain should not pretend they do not exist.
 */
export const DIMENSION_INTENTS: Readonly<Record<DimensionName, string>> = {
  FC: 'Base layer of spec validity and structural soundness.',
  SEC: 'Trust, risk posture, and security compliance.',
  DX: 'Clarity, completeness, and ingestion readiness for developers and tooling.',
  MR: "Assesses the API's readiness for mock testing and development.",
  ARAX: 'Semantic breadth, depth, and agent comprehension for AI systems.',
  AU: 'Functional utility, complexity comfort, and AI orchestration readiness.',
  AUD: 'Findability, semantic richness, and reasoning readiness.',
};

export type Severity = 'MAJOR' | 'MINOR';

/** A rule breach found while grading a specification. */
export interface Violation {
  readonly code: string;
  readonly detail: string;
  readonly severity: Severity;
}

/** One axis of the breakdown — what it measures and how the candidate did on it. */
export interface Dimension {
  readonly name: DimensionName;
  readonly intent: string;
  readonly score: Score;
  readonly grade: Grade;
}

/** The full grading of one candidate specification. */
export interface Scorecard {
  readonly score: Score;
  readonly grade: Grade;
  readonly dimensions: readonly Dimension[];
  readonly violations: readonly Violation[];
}

/**
 * A specification submitted for grading. Either `source` (a URI the scorer
 * dereferences) or `body` (the inlined spec content) carries the specification.
 */
export interface Candidate {
  readonly source?: URL;
  readonly contract?: ContractType;
  readonly body?: string;
}

/** The outcome of grading a {@link Candidate}. */
export interface Scoring {
  readonly id: string;
  readonly source: URL;
  readonly scorecard: Scorecard;
}

/**
 * A single rule breach observed by the scoring engine, already attributed to a
 * dimension. This is what the outbound engine port returns: observations, not a
 * verdict. Turning observations into a score is this module's policy, which is
 * what keeps the engine replaceable.
 */
export interface Finding {
  /** Identifier of the rule that fired, e.g. `info-description`. */
  readonly rule: string;
  readonly message: string;
  /** Where in the specification the rule fired, e.g. `paths./pets.get`. */
  readonly location: string;
  readonly severity: FindingSeverity;
  readonly dimension: DimensionName;
}

/** Engine-independent severity ladder. Ordered most to least serious. */
export const FINDING_SEVERITIES = ['ERROR', 'WARNING', 'INFO', 'HINT'] as const;
export type FindingSeverity = (typeof FINDING_SEVERITIES)[number];

/**
 * Score → letter grade. The single place the banding lives.
 *
 * The A/B/C/D bands match api-onboarding's `Score.Grade` (A 90-100, B 75-89,
 * C 50-74, D 0-49) so the two modules agree on what a number means; each band
 * is then split in thirds for the ± the contract asks for.
 */
export function gradeOf(score: Score): Grade {
  if (score >= 98) return 'A+';
  if (score >= 94) return 'A';
  if (score >= 90) return 'A-';
  if (score >= 85) return 'B+';
  if (score >= 80) return 'B';
  if (score >= 75) return 'B-';
  if (score >= 67) return 'C+';
  if (score >= 59) return 'C';
  if (score >= 50) return 'C-';
  if (score >= 34) return 'D+';
  if (score >= 17) return 'D';
  return 'D-';
}
