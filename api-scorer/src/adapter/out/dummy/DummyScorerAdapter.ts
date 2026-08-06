import type { ScorerDelegate } from '#appl/out/ScorerDelegate.js';
import type { ContractType, Finding } from '#domain/model.js';
import type { DimensionCoverage } from '#domain/scoring.js';

/**
 * Offline stand-in for the scoring engine, and the default so the module stays
 * deployable on its own — the same reason api-onboarding defaults its outbound
 * adapters to `dummy`.
 *
 * It sits at the *outbound* boundary rather than replacing the use case, so the
 * real orchestration, grading and error handling are exercised even here; only
 * the engine is fake.
 */
export class DummyScorerAdapter implements ScorerDelegate {
  /** A plausible rule inventory, so the real scoring arithmetic is exercised. */
  private static readonly COVERAGE: readonly DimensionCoverage[] = [
    { dimension: 'FC', enabled: { ERROR: 12, WARNING: 6 } },
    { dimension: 'SEC', enabled: { ERROR: 8, WARNING: 24 } },
    { dimension: 'DX', enabled: { ERROR: 4, WARNING: 14, INFO: 2 } },
    { dimension: 'MR', enabled: { ERROR: 6, WARNING: 5 } },
  ];

  /** Enough findings to produce a non-trivial scorecard, with no engine behind them. */
  private static readonly FINDINGS: readonly Finding[] = [
    {
      rule: 'dummy-info-description',
      message: 'Info object should have a description.',
      location: 'info',
      severity: 'WARNING',
      dimension: 'DX',
    },
    {
      rule: 'dummy-operation-security',
      message: 'Operation should define a security scheme.',
      location: 'paths./example.get',
      severity: 'ERROR',
      dimension: 'SEC',
    },
    {
      rule: 'dummy-media-example',
      message: 'Response should carry an example.',
      location: 'paths./example.get.responses.200',
      severity: 'INFO',
      dimension: 'MR',
    },
  ];

  coverage(_contract: ContractType): readonly DimensionCoverage[] {
    return DummyScorerAdapter.COVERAGE;
  }

  async evaluate(_specification: string, _contract: ContractType): Promise<readonly Finding[]> {
    return DummyScorerAdapter.FINDINGS;
  }
}
