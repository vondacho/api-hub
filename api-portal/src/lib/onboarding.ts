/**
 * Server-side client for api-onboarding's registration endpoint.
 *
 * Like the registry client, this only ever runs in the Node process: the
 * onboarding screen posts to the portal's own `/api/onboarding` route, which
 * calls onboarding over in-cluster DNS. The service itself is never exposed to
 * the browser.
 *
 * Shapes come from `src/types/onboarding.d.ts`, generated from the registration
 * contract (`npm run generate:onboarding-types`).
 */
import type { components, operations } from '../types/onboarding';

export type Candidate = components['schemas']['Candidate'];
export type CandidateProcessed = components['schemas']['CandidateProcessed'];
export type ProblemDetails = components['schemas']['ProblemDetails'];

type SubmitResponses = operations['submitCandidate']['responses'];

/** What the onboarding screen gets back, whichever way the submission went. */
export type SubmissionOutcome =
  | { kind: 'processed'; status: 200 | 201; body: SubmitResponses[200 | 201]['content']['application/json'] }
  | { kind: 'problem'; status: number; body: ProblemDetails }
  | { kind: 'unreachable'; message: string };

/** Read at call time — the chart injects it as an env var of the running container. */
function baseUrl(): string {
  return (
    process.env.ONBOARDING_BASE_URL ??
    import.meta.env.ONBOARDING_BASE_URL ??
    'http://api-onboarding:8080'
  );
}

/**
 * Submits a candidate. `source` is either a URL onboarding fetches the
 * specification from, or the specification document itself.
 */
export async function submitCandidate(source: Candidate['source']): Promise<SubmissionOutcome> {
  const url = `${baseUrl()}/api/v1/registrations`;

  let response: Response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json, application/problem+json' },
      body: JSON.stringify({ source } satisfies Candidate),
    });
  } catch (error) {
    return { kind: 'unreachable', message: `Onboarding could not be reached at ${url}: ${describe(error)}` };
  }

  const body = await response.json().catch(() => undefined);

  if ((response.status === 200 || response.status === 201) && body) {
    return { kind: 'processed', status: response.status, body: body as CandidateProcessed };
  }
  return {
    kind: 'problem',
    status: response.status,
    body: (body as ProblemDetails | undefined) ?? {
      status: response.status,
      title: response.statusText || 'Unexpected response',
      detail: `Onboarding answered ${response.status} without a problem document.`,
    },
  };
}

function describe(error: unknown): string {
  if (error instanceof Error) {
    const cause = (error as Error & { cause?: unknown }).cause;
    return cause instanceof Error ? `${error.message} (${cause.message})` : error.message;
  }
  return String(error);
}
