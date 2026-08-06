import { loadConfig } from './config.js';
import { buildServer } from './server.js';

/** Process entrypoint: read the environment, assemble the server, serve. */
const config = loadConfig();
const app = await buildServer(config);

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.once(signal, () => {
    app.log.info('%s received, shutting down', signal);
    void app.close().then(() => process.exit(0));
  });
}

try {
  await app.listen({ host: config.host, port: config.port });
} catch (error) {
  app.log.error({ err: error }, 'Failed to start');
  process.exit(1);
}
