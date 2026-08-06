import fp from 'fastify-plugin';
import type { FastifyError, FastifyReply, FastifyRequest } from 'fastify';
import { ScoringError } from '#appl/usecase/ScoringService.js';

/**
 * RFC 9457 error rendering, as the contract's 400/422 responses require.
 *
 * Registered through `fastify-plugin` so it escapes encapsulation and covers
 * every route, including ones added later. Problem `type` URIs come from the
 * SmartBear Problems Registry, matching what api-onboarding emits.
 */

const PROBLEM_CONTENT_TYPE = 'application/problem+json';
const REGISTRY = 'https://problems-registry.smartbear.com';

interface ErrorDetail {
  detail: string;
  pointer?: string;
  parameter?: string;
  header?: string;
  code?: string;
}

interface ProblemDetails {
  type: string;
  status: number;
  title: string;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: ErrorDetail[];
}

export const problemDetails = fp(async (app) => {
  app.setErrorHandler((error: FastifyError, request: FastifyRequest, reply: FastifyReply) => {
    const problem = toProblem(error, request);

    // 5xx is ours to explain, not the caller's to fix — keep the stack in the log.
    if (problem.status >= 500) {
      request.log.error({ err: error }, 'Unhandled error while serving %s %s', request.method, request.url);
    } else {
      request.log.info({ err: error }, 'Rejected %s %s: %s', request.method, request.url, problem.title);
    }

    return reply.code(problem.status).type(PROBLEM_CONTENT_TYPE).send(problem);
  });

  app.setNotFoundHandler((request: FastifyRequest, reply: FastifyReply) => {
    const problem: ProblemDetails = {
      type: 'about:blank',
      status: 404,
      title: 'Not found',
      detail: `No route matches ${request.method} ${request.url}.`,
      instance: request.url,
    };
    return reply.code(404).type(PROBLEM_CONTENT_TYPE).send(problem);
  });
});

function toProblem(error: FastifyError, request: FastifyRequest): ProblemDetails {
  // Schema validation lifted from the contract — the request parsed but does
  // not conform, which is the contract's 422 case.
  if (error.validation) {
    return {
      type: `${REGISTRY}/validation-error`,
      status: 422,
      title: 'Validation Error',
      detail: 'The request is invalid.',
      code: '422-02',
      instance: request.url,
      errors: error.validation.map((issue) => ({
        detail: issue.message ?? 'The value does not conform to the contract.',
        ...(issue.instancePath ? { pointer: issue.instancePath } : {}),
      })),
    };
  }

  // The application refused the candidate on its own terms.
  if (error instanceof ScoringError) {
    return {
      type: `${REGISTRY}/business-rule-violation`,
      status: 422,
      title: 'Business Rule Violation',
      detail: error.message,
      code: '422-01',
      instance: request.url,
      errors: error.violations.map((violation) => ({
        detail: violation.detail,
        code: violation.code,
      })),
    };
  }

  // Malformed JSON, empty body, wrong content type — Fastify already decided.
  const status = error.statusCode ?? 500;
  if (status >= 400 && status < 500) {
    return {
      type: 'about:blank',
      status,
      title: 'Bad Request',
      detail: error.message,
      instance: request.url,
      ...(error.code ? { code: error.code } : {}),
    };
  }

  return {
    type: 'about:blank',
    status: 500,
    title: 'Server Error',
    detail: 'The server encountered an unexpected error',
    instance: request.url,
  };
}
