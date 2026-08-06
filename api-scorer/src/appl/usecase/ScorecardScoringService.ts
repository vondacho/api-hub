import { randomUUID } from 'node:crypto';
import { UnsupportedContractError, type ScorerDelegate } from '#appl/out/ScorerDelegate.js';
import {
  SourceNotAllowedError,
  SpecificationUnreachableError,
  type SpecificationLoader,
} from '#appl/out/SpecificationLoader.js';
import { assemble, type DimensionWeights, type Penalties } from '#domain/scoring.js';
import { CONTRACT_TYPES, type Candidate, type ContractType, type Scoring } from '#domain/model.js';
import { ScoringError, type ScoringService } from './ScoringService.js';

export interface ScoringPolicy {
  readonly penalties?: Penalties;
  readonly weights?: DimensionWeights;
  readonly maxViolations?: number;
}

/**
 * The scoring use case: resolve a candidate to a specification, have the engine
 * evaluate it, assemble the result.
 *
 * It holds no knowledge of Spectral, of HTTP, or of the grading curve — those
 * live behind the two outbound ports and in `domain/scoring.ts` respectively.
 * What it does own is the orchestration and the translation of port failures
 * into `ScoringError`, which the web adapter renders as RFC 9457.
 */
export class ScorecardScoringService implements ScoringService {
  private readonly scorerDelegate: ScorerDelegate;
  private readonly specificationLoader: SpecificationLoader;
  private readonly scoringPolicy: ScoringPolicy;

  constructor(dependencies: {
    scorerDelegate: ScorerDelegate;
    specificationLoader: SpecificationLoader;
    scoringPolicy: ScoringPolicy;
  }) {
    this.scorerDelegate = dependencies.scorerDelegate;
    this.specificationLoader = dependencies.specificationLoader;
    this.scoringPolicy = dependencies.scoringPolicy;
  }

  async score(candidate: Candidate): Promise<Scoring> {
    const { source, content } = await this.resolve(candidate);
    const contract = candidate.contract ?? detectContract(content);

    try {
      const findings = await this.scorerDelegate.evaluate(content, contract);
      const scorecard = assemble({
        findings,
        coverage: this.scorerDelegate.coverage(contract),
        ...this.scoringPolicy,
      });
      return { id: randomUUID(), source, scorecard };
    } catch (error) {
      if (error instanceof UnsupportedContractError) {
        throw new ScoringError(error.message, [
          { code: 'UNSUPPORTED_CONTRACT', detail: error.message, severity: 'MAJOR' },
        ]);
      }
      throw error;
    }
  }

  /**
   * A candidate carries its specification either inline or by reference. Inline
   * content still needs a source in the response, so it gets a synthetic one.
   */
  private async resolve(candidate: Candidate): Promise<{ source: URL; content: string }> {
    if (candidate.body) {
      return { source: new URL(`urn:api-scorer:inline:${randomUUID()}`), content: candidate.body };
    }

    if (!candidate.source) {
      throw new ScoringError('The candidate carries neither a source nor a body.', [
        {
          code: 'CANDIDATE_EMPTY',
          detail: 'Provide either "source" (a URI to the specification) or "body" (the specification content).',
          severity: 'MAJOR',
        },
      ]);
    }

    try {
      return await this.specificationLoader.load(candidate.source);
    } catch (error) {
      // Both are the caller's problem, not ours, so both surface as violations
      // rather than a 500 — but they are kept distinct so a refused source is
      // never mistaken for an unreachable one.
      if (error instanceof SourceNotAllowedError) {
        throw new ScoringError(error.message, [
          { code: 'SOURCE_NOT_ALLOWED', detail: error.message, severity: 'MAJOR' },
        ]);
      }
      if (error instanceof SpecificationUnreachableError) {
        throw new ScoringError(error.message, [
          { code: 'SOURCE_UNREACHABLE', detail: error.message, severity: 'MAJOR' },
        ]);
      }
      throw error;
    }
  }
}

/**
 * Infer the contract type from the document itself when the caller did not say.
 * Same idea as `Contract.Type.findType(String)` in api-onboarding's domain.
 */
function detectContract(content: string): ContractType {
  const head = content.slice(0, 4096);

  // The version key may be anywhere a key can start, not just at the beginning
  // of a line: minified JSON puts it straight after `{` or a comma.
  const declares = (key: string): boolean =>
    new RegExp(String.raw`(?:^|[\s{,])["']?${key}["']?\s*:`, 'i').test(head);

  if (declares('asyncapi')) return 'ASYNCAPI';
  if (declares('openapi') || declares('swagger')) return 'OPENAPI';
  if (/\b(type|schema|interface)\s+\w+\s*\{/.test(head)) return 'GRAPHQLS';

  throw new ScoringError('The contract type could not be determined from the specification.', [
    {
      code: 'CONTRACT_UNDETERMINED',
      detail: `Set "contract" explicitly to one of ${CONTRACT_TYPES.join(', ')}.`,
      severity: 'MAJOR',
    },
  ]);
}
