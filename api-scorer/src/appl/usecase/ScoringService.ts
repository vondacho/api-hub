import type { Candidate, Scoring, Violation } from '#domain/model.js';

/**
 * The inbound port: what the application offers to the outside world. The web
 * adapter (`adapter/in/web/ScoringRestController.ts`) is the only caller today;
 * it depends on this interface, never on a concrete implementation.
 */
export interface ScoringService {
  /**
   * Grade a candidate specification.
   *
   * @throws {ScoringError} when the candidate cannot be graded — an unreadable
   *   source, an unsupported contract type, a specification that fails to parse.
   */
  score(candidate: Candidate): Promise<Scoring>;
}

/**
 * Application-level failure. Carries domain violations so the inbound adapter
 * can render them as RFC 9457 `errors` without knowing how they were produced.
 */
export class ScoringError extends Error {
  readonly violations: readonly Violation[];

  constructor(message: string, violations: readonly Violation[] = []) {
    super(message);
    this.name = 'ScoringError';
    this.violations = violations;
  }
}
