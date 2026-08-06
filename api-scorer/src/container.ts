import { diContainer } from '@fastify/awilix';
import { asClass, asValue, Lifetime } from 'awilix';
import type { ScorerDelegate } from '#appl/out/ScorerDelegate.js';
import type { SpecificationLoader } from '#appl/out/SpecificationLoader.js';
import { ScorecardScoringService, type ScoringPolicy } from '#appl/usecase/ScorecardScoringService.js';
import type { ScoringService } from '#appl/usecase/ScoringService.js';
import { DummyScorerAdapter } from '#adapter/out/dummy/DummyScorerAdapter.js';
import { HttpSpecificationLoader } from '#adapter/out/http/HttpSpecificationLoader.js';
import { SpectralScorerAdapter } from '#adapter/out/spectral/SpectralScorerAdapter.js';
import { compileRulesets } from '#adapter/out/spectral/RulesetFactory.js';
import type { Config } from './config.js';

/**
 * The composition root's wiring table — the one file that knows which concrete
 * class satisfies which port. Adapters and use cases never `new` each other.
 */

// Makes `diScope.resolve(...)` return the port type rather than any.
declare module '@fastify/awilix' {
  interface Cradle {
    scoringService: ScoringService;
    scorerDelegate: ScorerDelegate;
    specificationLoader: SpecificationLoader;
    scoringPolicy: ScoringPolicy;
    specMaxBytes: number;
    specFetchTimeoutMs: number;
  }
}

export async function registerDependencies(config: Config): Promise<void> {
  diContainer.register({
    scoringService: asClass(ScorecardScoringService, { lifetime: Lifetime.SINGLETON }),
    specificationLoader: asClass(HttpSpecificationLoader, { lifetime: Lifetime.SINGLETON }),
    scorerDelegate: await scorerDelegate(config),
    scoringPolicy: asValue({ maxViolations: config.maxViolations }),

    // Awilix injects the cradle and resolves by property name, so config values
    // a constructor reads must be registered individually, not as one blob.
    specMaxBytes: asValue(config.specMaxBytes),
    specFetchTimeoutMs: asValue(config.specFetchTimeoutMs),
  });
}

/**
 * Selects the engine, mirroring api-onboarding's `scorer.adapter` property.
 *
 * Ruleset compilation happens here, at startup: a ruleset that cannot be built
 * should stop the process, not surface as a 500 to whoever calls first.
 */
async function scorerDelegate(config: Config) {
  if (config.scorerAdapter === 'dummy') {
    return asClass(DummyScorerAdapter, { lifetime: Lifetime.SINGLETON });
  }

  const rulesets = await compileRulesets(config.evaluationPath);
  return asValue(
    new SpectralScorerAdapter({ rulesets, scoringTimeoutMs: config.scoringTimeoutMs }),
  );
}
