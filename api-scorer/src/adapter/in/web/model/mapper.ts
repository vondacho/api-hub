import type { Candidate, ContractType, Scoring } from '#domain/model.js';
import { CONTRACT_TYPES } from '#domain/model.js';
import type { components } from './scoring.js';

/**
 * The boundary between the wire contract and the domain.
 *
 * `components` comes from `npm run generate` (openapi-typescript over
 * `api/scoring/scoring_v1.openapi.yaml`) — never hand-edit it, and never let
 * these generated types leak past this file into `appl/` or `domain/`.
 */

export type CandidateDto = components['schemas']['Candidate'];
export type CandidateProcessedDto = components['schemas']['CandidateProcessed'];

/** Wire → domain. */
export function toCandidate(dto: CandidateDto): Candidate {
  return {
    ...(dto.source !== undefined ? { source: new URL(dto.source) } : {}),
    ...(dto.contract !== undefined ? { contract: toContractType(dto.contract) } : {}),
    ...(dto.body !== undefined ? { body: dto.body } : {}),
  };
}

/** Domain → wire. */
export function toCandidateProcessed(scoring: Scoring): CandidateProcessedDto {
  return {
    id: scoring.id,
    source: scoring.source.toString(),
    scorecard: {
      score: scoring.scorecard.score,
      grade: scoring.scorecard.grade as CandidateProcessedDto['scorecard']['grade'],
      dimensions: scoring.scorecard.dimensions.map((dimension) => ({
        name: dimension.name,
        intent: dimension.intent,
        score: dimension.score,
        grade: dimension.grade as CandidateProcessedDto['scorecard']['grade'],
      })),
      violations: scoring.scorecard.violations.map((violation) => ({
        code: violation.code,
        detail: violation.detail,
        severity: violation.severity,
      })),
    },
  };
}

/**
 * Schema validation has already restricted the value to the contract's enum, so
 * this only guards against the contract and the domain drifting apart.
 */
function toContractType(contract: string): ContractType {
  const known = CONTRACT_TYPES.find((type) => type === contract);
  if (!known) {
    throw new Error(`Contract type "${contract}" is declared in the contract but unknown to the domain.`);
  }
  return known;
}
