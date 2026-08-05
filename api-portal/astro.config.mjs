// @ts-check
import { defineConfig } from 'astro/config';
import node from '@astrojs/node';

// https://astro.build/config
export default defineConfig({
  // Server-rendered. The Catalogue reads api-onboarding over in-cluster DNS
  // (http://api-onboarding:8080), so those calls have to happen server-side —
  // a static bundle could only reach it from the browser, which would mean
  // exposing the onboarding service publicly.
  output: 'server',
  adapter: node({ mode: 'standalone' }),
});
