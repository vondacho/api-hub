import $RefParser from '@apidevtools/json-schema-ref-parser';
import type { FastifySchema } from 'fastify';

/**
 * Contract-first route schemas.
 *
 * Rather than hand-writing JSON Schema for the routes — which would duplicate
 * the contract and let the two drift — the OpenAPI document is dereferenced at
 * boot and the request/response schemas are lifted straight out of it. Changing
 * `api/scoring/scoring_v1.openapi.yaml` changes what the server accepts, with
 * no code edit.
 *
 * Dereferencing (rather than bundling) also resolves the external
 * `../problem/problem_rfc_v1.openapi.json` reference, which Ajv could not
 * follow on its own.
 */

type JsonObject = Record<string, unknown>;

export interface Contract {
  /** Schemas for the operation with the given `operationId`, ready for `app.route({ schema })`. */
  schemaFor(operationId: string): FastifySchema;
}

/** Load and dereference the OpenAPI document at `specPath`. */
export async function loadContract(specPath: string): Promise<Contract> {
  const document = (await $RefParser.dereference(specPath)) as JsonObject;
  const operations = indexByOperationId(document);

  return {
    schemaFor(operationId: string): FastifySchema {
      const operation = operations.get(operationId);
      if (!operation) {
        throw new Error(`Operation "${operationId}" is not declared in ${specPath}.`);
      }
      return toFastifySchema(operation);
    },
  };
}

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'];

function indexByOperationId(document: JsonObject): Map<string, JsonObject> {
  const operations = new Map<string, JsonObject>();
  const paths = (document['paths'] ?? {}) as Record<string, JsonObject>;

  for (const item of Object.values(paths)) {
    for (const method of HTTP_METHODS) {
      const operation = item[method] as JsonObject | undefined;
      const operationId = operation?.['operationId'];
      if (operation && typeof operationId === 'string') {
        operations.set(operationId, operation);
      }
    }
  }
  return operations;
}

/**
 * Project one OpenAPI operation onto Fastify's schema shape. Only the JSON
 * media types are carried over — the contract declares no others, and a
 * silently ignored `text/*` body would be worse than a hard failure later.
 */
function toFastifySchema(operation: JsonObject): FastifySchema {
  const schema: FastifySchema = {};

  const body = jsonSchemaOf(operation['requestBody'] as JsonObject | undefined);
  if (body) schema.body = body;

  const responses = operation['responses'] as Record<string, JsonObject> | undefined;
  if (responses) {
    const byStatus: Record<string, JsonObject> = {};
    for (const [status, response] of Object.entries(responses)) {
      const responseSchema = jsonSchemaOf(response);
      if (responseSchema) byStatus[status] = responseSchema;
    }
    if (Object.keys(byStatus).length > 0) schema.response = byStatus;
  }

  return schema;
}

/** Pull `content['application/json' | 'application/problem+json'].schema` out of a body/response object. */
function jsonSchemaOf(carrier: JsonObject | undefined): JsonObject | undefined {
  const content = carrier?.['content'] as Record<string, JsonObject> | undefined;
  if (!content) return undefined;

  const mediaType = Object.keys(content).find(
    (type) => type === 'application/json' || type.endsWith('+json'),
  );
  if (!mediaType) return undefined;

  return content[mediaType]?.['schema'] as JsonObject | undefined;
}
