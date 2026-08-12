// @ts-check
import { defineConfig } from 'astro/config';
import node from '@astrojs/node';
import starlight from '@astrojs/starlight';
import tailwindcss from '@tailwindcss/vite';

// https://astro.build/config
export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  vite: { plugins: [tailwindcss()] },
  integrations: [
    starlight({
      title: 'Documentation',
      prerender: true,
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
