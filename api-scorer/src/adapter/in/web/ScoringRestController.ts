import type { FastifyPluginAsync } from 'fastify';
import type { Contract } from './contract.js';
import { toCandidate, toCandidateProcessed, type CandidateDto } from './model/mapper.js';

export interface ScoringRestControllerOptions {
  readonly contract: Contract;
}

/**
 * The inbound adapter: it translates HTTP into a call on the inbound port
 * (`ScoringService`) and the result back into the contract's response shape.
 *
 * It holds no scoring logic of its own, and it resolves its collaborator from
 * the request's DI scope rather than importing an implementation — swapping
 * `DummyScoringService` for the real one is a change to `container.ts` alone.
 */
export const scoringRestController: FastifyPluginAsync<ScoringRestControllerOptions> = async (
  app,
  { contract },
) => {
  app.post<{ Body: CandidateDto }>(
    '/scorings',
    { schema: contract.schemaFor('scoreCandidate') },
    async (request, reply) => {
      const scoringService = request.diScope.resolve('scoringService');

      const scoring = await scoringService.score(toCandidate(request.body));

      return reply.code(200).send(toCandidateProcessed(scoring));
    },
  );
};
