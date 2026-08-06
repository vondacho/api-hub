/**
 * Outbound port for retrieving a specification the caller referenced by URI
 * rather than inlined.
 *
 * Separate from the scoring engine on purpose: fetching is its own external
 * concern, with its own failure modes, its own resilience and — because the
 * service dereferences URIs an untrusted caller chose — its own trust boundary.
 */
export interface SpecificationLoader {
  /**
   * Retrieve the specification at `source`.
   *
   * @throws {SpecificationUnreachableError} when the source cannot be read.
   * @throws {SourceNotAllowedError} when the source is not permitted.
   */
  load(source: URL): Promise<LoadedSpecification>;
}

export interface LoadedSpecification {
  /** The URI the content actually came from — after redirects, if any. */
  readonly source: URL;
  readonly content: string;
}

/** The source was reachable in principle but could not be read. */
export class SpecificationUnreachableError extends Error {
  constructor(source: URL, reason: string) {
    super(`The specification at [${source.href}] could not be read: ${reason}`);
    this.name = 'SpecificationUnreachableError';
  }
}

/**
 * The source is refused by policy rather than by circumstance — a scheme we do
 * not fetch, or an address that is not a legitimate place for a public
 * specification to live.
 */
export class SourceNotAllowedError extends Error {
  constructor(source: URL, reason: string) {
    super(`The source [${source.href}] is not allowed: ${reason}`);
    this.name = 'SourceNotAllowedError';
  }
}
