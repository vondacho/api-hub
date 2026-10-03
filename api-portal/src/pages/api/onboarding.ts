import type { APIRoute } from 'astro';
import { submitCandidate } from '../../lib/onboarding';

// Rendered on demand: the documentation is prerendered, but this route has to
// reach api-onboarding at request time, from the server.
export const prerender = false;

/** Generous for a contract, small enough that a stray upload cannot tie up the server. */
const MAX_SOURCE_BYTES = 5 * 1024 * 1024;

const json = (body: unknown, status: number) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

/**
 * Relays an onboarding submission from the browser to api-onboarding and hands
 * back a {@link SubmissionOutcome}. Always answers 200 once the submission was
 * relayed — the outcome, including onboarding's own status, is in the body — so
 * the screen tells a rejected contract apart from a broken relay.
 */
export const POST: APIRoute = async ({ request }) => {
  const payload = await request.json().catch(() => undefined);
  const source: unknown = payload?.source;

  if (typeof source !== 'string' || source.trim() === '') {
    return json({ message: 'Provide a URL or a specification document as `source`.' }, 400);
  }
  if (new TextEncoder().encode(source).byteLength > MAX_SOURCE_BYTES) {
    return json({ message: `The specification exceeds ${MAX_SOURCE_BYTES / 1024 / 1024} MB.` }, 413);
  }

  return json(await submitCandidate(source), 200);
};
