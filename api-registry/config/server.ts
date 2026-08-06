import type { Core } from '@strapi/strapi';

const config = ({ env }: Core.Config.Shared.ConfigParams): Core.Config.Server => ({
  host: env('HOST', '0.0.0.0'),
  port: env.int('PORT', 1337),
  // Public origin, as the browser sees it. Behind an ingress that differs from
  // host:port, and Strapi uses it to build the absolute URLs it hands to the
  // admin panel — leave it empty and the UI calls back to the in-cluster
  // address, which does not resolve from the browser.
  url: env('URL', ''),
  app: {
    keys: env.array('APP_KEYS'),
  },
});

export default config;
