import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';
import {
  SourceNotAllowedError,
  SpecificationUnreachableError,
  type LoadedSpecification,
  type SpecificationLoader,
} from '#appl/out/SpecificationLoader.js';

/** Redirect hops followed before giving up. */
const MAX_REDIRECTS = 3;

/** Transport attempts, including the first. */
const ATTEMPTS = 3;

/**
 * Fetches a specification over HTTP(S).
 *
 * Every legitimate source is a public URL outside the cluster, which is what
 * makes the guarding here cheap: it refuses nothing a real caller would send.
 * Without it the service is an SSRF proxy — a caller who cannot reach
 * `api-registry` or the node metadata endpoint could ask *us* to read them and
 * hand back the response inside a violation message.
 *
 * Resilience (timeout, retry) lives here rather than in the use case, per the
 * repo's hexagonal conventions.
 */
export class HttpSpecificationLoader implements SpecificationLoader {
  private readonly maxBytes: number;
  private readonly timeoutMs: number;

  // Awilix injects the container's cradle, and reading a name it does not know
  // throws — so this constructor may only reference registered dependencies.
  constructor(dependencies: { specMaxBytes: number; specFetchTimeoutMs: number }) {
    this.maxBytes = dependencies.specMaxBytes;
    this.timeoutMs = dependencies.specFetchTimeoutMs;
  }

  async load(source: URL): Promise<LoadedSpecification> {
    let current = source;

    for (let redirect = 0; redirect <= MAX_REDIRECTS; redirect += 1) {
      await this.assertAllowed(current);
      const response = await this.get(current);

      const location = response.headers.get('location');
      if (isRedirect(response.status) && location) {
        // Re-check every hop. Following redirects blindly would reopen exactly
        // what the first check closed: a public URL can redirect to 127.0.0.1.
        current = new URL(location, current);
        continue;
      }

      if (!response.ok) {
        throw new SpecificationUnreachableError(source, `HTTP ${response.status}`);
      }
      return { source: current, content: await this.read(response, source) };
    }

    throw new SpecificationUnreachableError(source, `more than ${MAX_REDIRECTS} redirects`);
  }

  /** Scheme and address policy. Applied to the original URL and to every hop. */
  private async assertAllowed(source: URL): Promise<void> {
    if (source.protocol !== 'http:' && source.protocol !== 'https:') {
      throw new SourceNotAllowedError(source, `the scheme "${source.protocol}" is not fetched`);
    }

    const host = source.hostname.replace(/^\[|]$/g, '');
    const addresses = isIP(host) ? [host] : await this.resolve(source, host);

    for (const address of addresses) {
      if (isPrivateAddress(address)) {
        throw new SourceNotAllowedError(
          source,
          `it resolves to the non-public address ${address}; specifications must be publicly reachable`,
        );
      }
    }
  }

  private async resolve(source: URL, host: string): Promise<string[]> {
    try {
      const results = await lookup(host, { all: true });
      return results.map((result) => result.address);
    } catch (error) {
      throw new SpecificationUnreachableError(source, `the host ${host} could not be resolved`);
    }
  }

  /** Retries only the transport; a refusal by policy is never retried. */
  private async get(source: URL): Promise<Response> {
    let lastReason = 'unknown error';

    for (let attempt = 1; attempt <= ATTEMPTS; attempt += 1) {
      try {
        return await fetch(source, {
          redirect: 'manual',
          signal: AbortSignal.timeout(this.timeoutMs),
          headers: { accept: 'application/json, application/yaml, text/yaml, text/plain, */*' },
        });
      } catch (error) {
        lastReason = error instanceof Error ? error.message : String(error);
        if (attempt < ATTEMPTS) {
          await delay(2 ** (attempt - 1) * 200);
        }
      }
    }

    throw new SpecificationUnreachableError(source, lastReason);
  }

  /**
   * Streams the body so an oversized source is abandoned as the cap is passed,
   * rather than after it has already been buffered.
   */
  private async read(response: Response, source: URL): Promise<string> {
    const declared = Number(response.headers.get('content-length'));
    if (Number.isFinite(declared) && declared > this.maxBytes) {
      throw new SourceNotAllowedError(source, `it is larger than ${this.maxBytes} bytes`);
    }

    if (!response.body) return '';

    const decoder = new TextDecoder();
    const chunks: string[] = [];
    let size = 0;

    for await (const chunk of response.body as unknown as AsyncIterable<Uint8Array>) {
      size += chunk.byteLength;
      if (size > this.maxBytes) {
        await response.body.cancel().catch(() => undefined);
        throw new SourceNotAllowedError(source, `it is larger than ${this.maxBytes} bytes`);
      }
      chunks.push(decoder.decode(chunk, { stream: true }));
    }
    chunks.push(decoder.decode());

    return chunks.join('');
  }
}

function isRedirect(status: number): boolean {
  return status === 301 || status === 302 || status === 303 || status === 307 || status === 308;
}

/**
 * Loopback, private, link-local, CGNAT and unique-local ranges. In-cluster names
 * such as `api-registry` resolve into exactly these, which is the point.
 */
function isPrivateAddress(address: string): boolean {
  if (isIP(address) === 6) {
    const v6 = address.toLowerCase();
    // IPv4-mapped addresses (::ffff:127.0.0.1) must be judged as IPv4.
    const mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/.exec(v6);
    if (mapped?.[1]) return isPrivateAddress(mapped[1]);

    return (
      v6 === '::1' ||
      v6 === '::' ||
      v6.startsWith('fc') || // unique local
      v6.startsWith('fd') ||
      v6.startsWith('fe8') || // link local
      v6.startsWith('fe9') ||
      v6.startsWith('fea') ||
      v6.startsWith('feb')
    );
  }

  const octets = address.split('.').map(Number);
  if (octets.length !== 4 || octets.some((octet) => !Number.isInteger(octet))) return true;
  const [a = 0, b = 0] = octets;

  return (
    a === 0 || // "this" network
    a === 10 ||
    a === 127 || // loopback
    (a === 100 && b >= 64 && b <= 127) || // CGNAT
    (a === 169 && b === 254) || // link local, incl. cloud metadata
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) ||
    a >= 224 // multicast and reserved
  );
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
