import type { FastifyPluginAsync } from 'fastify';

/**
 * Probe targets for the Helm chart's startup/liveness/readiness checks.
 * Deliberately outside the contract's `/api/v1` base path — this answers
 * whether the process is serving, not whether the scoring API is healthy.
 */
export const healthController: FastifyPluginAsync = async (app) => {
  app.get('/healthz', async () => ({ status: 'UP' }));
};
