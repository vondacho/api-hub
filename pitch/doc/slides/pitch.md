---
marp: true
header: 'Pitch - Living documentation - API Hub v2'
footer: 'Technical architecture - Pictet Tech, OVD, Q3-2026'
---

<!-- theme: uncover -->
<!-- size: 16:9 -->
<!-- class: invert -->

<style>
section {
  font-size: 26px;
  padding: 50px 60px;
}
section h1 {
  font-size: 1.7em;
  margin-bottom: 0.4em;
}
section h2 {
  font-size: 1.4em;
}
section ul {
  font-size: 0.95em;
  line-height: 1.4;
}
section table {
  font-size: 0.6em;
  margin: 0 auto;
}
section table th,
section table td {
  padding: 0.25em 0.6em;
  text-align: left;
}
section strong {
  color: var(--color-highlight-heading);
}
header,
footer {
  font-size: 0.55em;
}
</style>

**Wiki** documentation is **intention**-based, and often outdated.
**Living** documentation is **up-to-date**, **automated**. 

To close the gap between intended and really deployed.

---

# Living documentation hub

A set of **connected web portals** for every perspective, organized by product.

| Portal      | Perspective             | Audience        |
|-------------|-------------------------|-----------------|
| [doc-hub](http://doc-portal.obya.ch)     | Product 360             | PM, BA, Support |
| [ba-hub](http://ba-portal.obya.ch)      | Business analysis       | BA              |
| [arch-hub](http://arch-portal.obya.ch)    | Software architecture   | SA, TA, Dev     |
| [api-hub](http://api-portal.obya.ch)     | **API-first**               | SA, TA, Dev, BA |
| [dev-hub](http://dev-portal.obya.ch)     | Software development    | Dev             |
| [qa-hub](http://qa-portal.obya.ch)      | Quality assurance       | QA              |
| ux-hub      | User experience         | UX, dev         |

---

# API hub v2

| api-hub v1   | api-hub v2                          | arch-hub v1<br/>ba-hub v1 |
|--------------|-------------------------------------|---------------------------|
| onboarding?  | onboarding (discovery)              | -                         |
| REST,?       | REST, GraphQL, Grpc, Async          | -                         |
| scoring?     | scoring (CF, SEC, DX, MR, AX)       | -                         |
| catalog      | catalog (apis, products)            | -                         |
| versioning?  | versioning (vs revision)            | -                         |
| registry?    | registry (CMS)                      | -                         |
| discovery?   | discovery pipeline                  | -                         |
| -            | monitoring (admin, metrics, change) | -                         |
| -            | metrics (sli, slo)                  | -                         |
| -            | change management                   | -                         |
| -            | CF-mocking (local, at-scale)        | -                         |
| -            | tools (CF-codegen, CF-mocking)      | -                         |
| -            | design guidelines                   | -                         |
| -            | MCP (exposure, catalog, CF-codegen) | -                         |
| dependencies | -                                   | dependencies              |

---

# Content management

The content is mainly provisioned by **automation**, ie, CI.CD pipelines 
on 'develop' branch, campaigns executions, HELM deployments, and observability.

---

# Solution proposal

- Content-driven web portal with [Astro](https://astro.build/)
- [Microservices] architecture [C4](https://arch-likec4.obya.ch/), [Events](https://arch-eventcatalog.obya.ch/)
- Registry with [Strapi](https://strapi.io/) CMS
- API-Ops on registered _repositories/layout.yaml_
- [Spectral]() or [Jentic]() scorer
- [Microcks](https://microcks.io/) for contract-first API mocking and conformance testing
- [reshapr](https://reshapr.io/), your API as an MCP server
- [solo.io](https://www.solo.io/products/agentregistry), an MCP server registry
- SpringBoot, Node.js, Typescript, PostgresSQL/MongoDB
- Kubernetes/Helm
- OTEL

---

# Plan

- MVP (**DCP**): **API catalog**, **C4 workspace**, and **EventCatalog**
- MVP (DCP): **api-hub**, **arch-hub**
- R1 (DCP): **dev-hub**, **ba-hub**
- R2 (DCP): **qa-hub**, **doc-hub**
- To work on the **observability** topic
- To promote the Living Documentation Hub @ **Pictet Tech**
- To onboard **Dev** teams with their **APIs** and **C4** workspaces
- To onboard **QA** and **UX** teams: **qa-hub**, **ux-hub**
- To build Living documentation/**MCP** use cases

---

## Thanks.

>Stay **up-to-date**, stay informed, and stay ahead with our **Living Documentation Hub**.

---

## Links

Enterprise architect: Patrick Doyle
Innovation: Sebastien Gille
API hub: Carine Leroux
PWM-AI governance: Luis
