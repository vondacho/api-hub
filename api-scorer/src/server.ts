import { fastifyAwilixPlugin } from '@fastify/awilix';
import Fastify, { type FastifyInstance } from 'fastify';
import { healthController } from '#adapter/in/web/HealthController.js';
import { problemDetails } from '#adapter/in/web/problem.js';
import { scoringRestController } from '#adapter/in/web/ScoringRestController.js';
import { loadContract } from '#adapter/in/web/contract.js';
import type { Config } from './config.js';
import { registerDependencies } from './container.js';

/**
 * Builds a fully wired server without starting it, so the same assembly can be
 * driven by `app.inject()` from tests later on.
 */
export async function buildServer(config: Config): Promise<FastifyInstance> {
  const app = Fastify({
    logger: { level: config.logLevel },
    ajv: {
      customOptions: {
        // The route schemas come from an OpenAPI 3.1 document, which carries
        // annotation keywords (`example`, `description` on enums, …) that Ajv's
        // strict mode rejects outright.
        strict: false,
        allErrors: true,
      },
    },
  });

  await app.register(fastifyAwilixPlugin, {
    disposeOnClose: true,
    disposeOnResponse: true,
    strictBooleanEnforced: true,
  });
  await registerDependencies(config);

  await app.register(problemDetails);
  await app.register(healthController);

  // Dereferenced once at boot rather than per request; a contract that cannot
  // be read is a startup failure, not a 500 discovered by the first caller.
  const contract = await loadContract(config.specPath);
  await app.register(scoringRestController, { prefix: config.basePath, contract });

  return app;
}
