import spectralCore from '@stoplight/spectral-core';
import Parsers from '@stoplight/spectral-parsers';
import { Resolver } from '@stoplight/json-ref-resolver';
import { UnsupportedContractError, type ScorerDelegate } from '#appl/out/ScorerDelegate.js';
import type { ContractType, Finding } from '#domain/model.js';
import type { DimensionCoverage } from '#domain/scoring.js';
import type { CompiledRuleset } from './RulesetFactory.js';
import { toFindingSeverity } from './severity.js';

// Spectral is CommonJS; named ESM imports of it fail at runtime.
const { Spectral, Document } = spectralCore;

/**
 * The Spectral outbound adapter — the only place Spectral's vocabulary exists.
 *
 * Its whole job is translation: Spectral emits flat diagnostics carrying a rule
 * code, a JSON path and a numeric severity; the domain wants findings attributed
 * to a dimension. Nothing Spectral-shaped is allowed past this class.
 */
export class SpectralScorerAdapter implements ScorerDelegate {
  private readonly rulesets: ReadonlyMap<ContractType, CompiledRuleset>;
  private readonly timeoutMs: number;

  constructor(dependencies: {
    rulesets: ReadonlyMap<ContractType, CompiledRuleset>;
    scoringTimeoutMs: number;
  }) {
    this.rulesets = dependencies.rulesets;
    this.timeoutMs = dependencies.scoringTimeoutMs;
  }

  coverage(contract: ContractType): readonly DimensionCoverage[] {
    return this.rulesets.get(contract)?.coverage ?? [];
  }

  async evaluate(specification: string, contract: ContractType): Promise<readonly Finding[]> {
    const compiled = this.rulesets.get(contract);
    if (!compiled) throw new UnsupportedContractError(contract);

    const spectral = new Spectral({ resolver: REFUSING_RESOLVER });
    spectral.setRuleset(compiled.ruleset);

    // The YAML parser also accepts JSON, YAML being a superset of it.
    const document = new Document(specification, Parsers.Yaml, '/candidate');

    // A pathological document can keep the linter busy indefinitely; the caller
    // is waiting on an HTTP request, so cap it rather than hang the connection.
    const diagnostics = await withTimeout(
      spectral.run(document),
      this.timeoutMs,
      `Scoring did not complete within ${this.timeoutMs}ms.`,
    );

    return diagnostics.map((diagnostic) => {
      const rule = String(diagnostic.code);
      return {
        rule,
        message: diagnostic.message,
        location: (diagnostic.path ?? []).join('.'),
        severity: toFindingSeverity(diagnostic.severity),
        dimension: compiled.dimensionOf.get(rule) ?? 'FC',
      };
    });
  }
}

/**
 * Spectral resolves `$ref`s over http and file by default. That is a second way
 * out of this process, independent of the specification loader: a submitted
 * document containing `$ref: http://api-registry:1337/...` would be fetched by
 * Spectral itself, with none of the loader's checks applied.
 *
 * Registering no protocol resolvers closes it — every external `$ref` is left
 * unresolved and reported instead of fetched. Internal refs (`#/components/...`)
 * never reach a resolver, so they keep working.
 */
const REFUSING_RESOLVER = new Resolver({ resolvers: {} });

async function withTimeout<T>(work: Promise<T>, ms: number, message: string): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  try {
    return await Promise.race([
      work,
      new Promise<never>((_, reject) => {
        timer = setTimeout(() => reject(new Error(message)), ms);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}
