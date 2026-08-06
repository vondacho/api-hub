import type { ContractType, Finding } from '#domain/model.js';
import type { DimensionCoverage } from '#domain/scoring.js';

/**
 * Outbound port for the scoring engine.
 *
 * It returns **findings, not a scorecard**: the engine reports what it observed,
 * and `domain/scoring.ts` decides what those observations are worth. Any engine
 * that can lint a specification and attribute breaches to a dimension satisfies
 * this port, which is what keeps Spectral replaceable.
 */
export interface ScorerDelegate {
  /**
   * Evaluate a specification.
   *
   * @throws {UnsupportedContractError} when no ruleset covers `contract`.
   */
  evaluate(specification: string, contract: ContractType): Promise<readonly Finding[]>;

  /**
   * What the engine could have checked for `contract`: per dimension, the
   * enabled rules counted by severity.
   *
   * Reported separately from the findings for two reasons. "No findings" and
   * "not measured" must not look alike — a dimension nobody checked would
   * otherwise score a perfect 100. And scoring needs a denominator: a score is
   * the share of the applicable rules satisfied, so how many rules were in play
   * is part of the answer, not an implementation detail.
   */
  coverage(contract: ContractType): readonly DimensionCoverage[];
}

/** No ruleset covers the requested contract type. */
export class UnsupportedContractError extends Error {
  readonly contract: string;

  constructor(contract: string) {
    super(`No scoring ruleset is available for contract type "${contract}".`);
    this.name = 'UnsupportedContractError';
    this.contract = contract;
  }
}
