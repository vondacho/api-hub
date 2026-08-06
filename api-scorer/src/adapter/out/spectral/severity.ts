import type { FindingSeverity } from '#domain/model.js';

/**
 * Spectral's numeric severity ladder → the domain's named one.
 *
 * Shared by the adapter (translating diagnostics) and the ruleset factory
 * (counting what each dimension could check), which must agree: the two halves
 * of a dimension score are counted on the same scale.
 */
export function toFindingSeverity(severity: number): FindingSeverity {
  switch (severity) {
    case 0:
      return 'ERROR';
    case 1:
      return 'WARNING';
    case 2:
      return 'INFO';
    default:
      return 'HINT';
  }
}
