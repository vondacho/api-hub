// @ts-check
import { defineConfig } from 'astro/config';
import node from '@astrojs/node';
import starlight from '@astrojs/starlight';
import tailwindcss from '@tailwindcss/vite';

// https://astro.build/config
export default defineConfig({
  // Server-rendered. The Catalogue reads api-onboarding over in-cluster DNS
  // (http://api-onboarding:8080), so those calls have to happen server-side —
  // a static bundle could only reach it from the browser, which would mean
  // exposing the onboarding service publicly.
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  // Tailwind 4 is a Vite plugin, not an Astro integration. src/styles/global.css
  // is imported only by the portal's own Layout, so Tailwind's preflight never
  // reaches the Starlight pages under /doc/* — the two themes stay separate and
  // no Starlight/Tailwind compatibility shim is needed.
  vite: { plugins: [tailwindcss()] },
  integrations: [
    starlight({
      title: 'Documentation',
      // Documentation is prose: it has no reason to be rendered per request,
      // and Pagefind builds its index from the emitted HTML — Starlight
      // refuses to enable search when prerendering is off. Only the Starlight
      // routes are affected; the Catalogue pages stay on-demand.
      prerender: true,
      // Content lives under src/content/docs/doc/, so Starlight owns /doc/*
      // and leaves the site root to the portal's own home page.
      sidebar: [
        { label: 'Onboarding', link: '/doc/onboarding/' },
        {
          label: 'Design guidelines',
          items: [
            { label: 'Overview', link: '/doc/design-guidelines/' },
            { label: 'Scoring', link: '/doc/design-guidelines/scoring/' },
          ],
        },
        { label: 'Catalog', link: '/doc/catalog/' },
        {
          label: 'Usage',
          items: [
            { label: 'Integration', link: '/doc/usage/integration/' },
            { label: 'Mocking', link: '/doc/usage/mocking/' },
            { label: 'MCP', link: '/doc/usage/mcp/' },
          ],
        },
      ],
    }),
  ],
});
