/**
 * Read-side client for the API registry.
 *
 * The Catalogue reads the registry directly rather than going through
 * api-onboarding, which exposes no query API. This only works because the portal
 * is server-rendered: every call here happens in the Node process over in-cluster
 * DNS, so the registry is never reached from the browser and stays unexposed.
 *
 * Response shapes come from `src/types/registry.d.ts`, generated from the
 * registry's OpenAPI contract (`npm run generate:registry-types`). Nothing here
 * hand-writes a type that the contract already describes.
 */
import type { operations } from '../types/registry';

type SearchResponse =
  operations['specification/get/specifications']['responses'][200]['content']['application/json'];

/** One registered specification, as the catalogue lists it. */
export type Specification = SearchResponse['data'][number];

/** The fields a catalogue card shows — `body` is deliberately not among them. */
const SUMMARY_FIELDS = [
  'specId',
  'name',
  'productName',
  'version',
  'revision',
  'bundleName',
  'componentName',
  'componentRevision',
  'contract',
] as const;

/**
 * Read at call time, not at module load: the chart injects this as an env var,
 * so it only exists in the running container. `import.meta.env` covers the dev
 * server, where the value comes from a .env file instead.
 */
function baseUrl(): string {
  return (
    process.env.REGISTRY_BASE_URL ??
    import.meta.env.REGISTRY_BASE_URL ??
    'http://api-registry:1337/api'
  );
}

async function get<T>(path: string, params: URLSearchParams): Promise<T> {
  const url = `${baseUrl()}${path}?${params}`;
  const response = await fetch(url, { headers: { accept: 'application/json' } });

  if (!response.ok) {
    // The status matters to the caller — a 403 means the registry's Public role
    // is missing its `specification` permissions, which is a deployment problem
    // rather than an empty catalogue.
    throw new Error(`Registry answered ${response.status} for ${path}`);
  }
  return (await response.json()) as T;
}

/**
 * Specifications whose name contains `query`, case-insensitively. An empty query
 * lists everything — the catalogue opens on the full inventory rather than on a
 * blank page.
 */
export async function searchSpecifications(query: string): Promise<Specification[]> {
  const params = new URLSearchParams();
  for (const field of SUMMARY_FIELDS) params.append('fields', field);
  params.append('sort', 'name:asc');
  params.append('status', 'published');

  const trimmed = query.trim();
  if (trimmed) params.append('filters[name][$containsi]', trimmed);

  const { data } = await get<SearchResponse>('/specifications', params);
  return data;
}

/** One specification including its contract source, for the detail page. */
export async function specificationAt(documentId: string): Promise<Specification | undefined> {
  const params = new URLSearchParams();
  params.append('status', 'published');

  try {
    const { data } = await get<{ data: Specification }>(`/specifications/${documentId}`, params);
    return data ?? undefined;
  } catch {
    // A missing document is a 404 from the registry; the page turns that into
    // its own 404 rather than a 500.
    return undefined;
  }
}

/** True for the contract types the reference widget can render. */
export function isOpenApi(contract: Specification['contract']): boolean {
  return contract.startsWith('OPENAPI_');
}

/** `OPENAPI_V30` reads as `OpenAPI 3.0` on a card. */
export function contractLabel(contract: Specification['contract']): string {
  const [family, version] = contract.split('_');
  const names: Record<string, string> = {
    OPENAPI: 'OpenAPI',
    ASYNCAPI: 'AsyncAPI',
    GRAPHQLS: 'GraphQL',
    WSDL: 'WSDL',
    OVERLAY: 'Overlay',
  };
  const name = names[family ?? ''] ?? contract;
  if (!version) return name;
  const digits = version.replace('V', '');
  return `${name} ${digits.split('').join('.')}`;
}
