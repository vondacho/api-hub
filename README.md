# My API Portal
A system to support API management. It provides the following features:

- Onboarding (REST/Async/SOAP/GraphQL)
- Registry
- Catalog
- Scoring
- Monitoring
- Crawling
- Mocking
- MCP wrapping
- MCP registry
- Documentation (Design guidelines, Use cases, Change management)

## Architecture
The system is supported by a micro-services architecture where every service is accountable
for one feature.

### Onboarding component
This component provides a REST API to submit API specifications for onboarding. Every candidate is validated
and scored before its effective registration.

### Scoring component
This component provides a REST API to evaluate API specifications against a set of design rules. It is based on
well known scoring engines like [Spectral](https://github.com/stoplightio/spectral-rulesets) and [Jentic](https://github.com/jentic).

### Registry component
This component provides a REST API to persist validated and scored API specifications. It is based on [Strapi](https://strapi.io/).

### Catalog component
This component provides a web catalog view with list and search capabilities. It is based on [Astro.js](https://astro.build/).

### Monitoring component
This component observes deployments and then inject metadata about endpoint metrics and implementing component into the
registered API specifications.

It provides provides both a REST API and a web view to administrate the registered API specifications.
It notifies subscribers about new versions and supports change management.

### Crawler component
This component hooks commits done on registered codebase repositories where API specifications are stored, and then orders
the onboarding of new revisions.

### Mocking component
This component launches containerized API mocks on-demand which behavior is driven by their underlying contract and
based on static or ai-powered dynamic examples. It is based on [Microcks](https://microcks.io/).

### MCP wrapper component
This component produces an MCP wrapper around registered API specifications exposing them as agents. It is based on [reShapr](https://reshapr.io/).

### MCP registry component
This component is an Agent registry of wrapped API specifications. It is based on [Solo](https://www.solo.io/products/agentregistry).

### Documentation component
This component provides a web documentation about onboarding, design guidelines, use cases, change management. It is based on [Astro.js](https://astro.build/).

## Development methods
Contract-driven and Test-driven practices are first citizens for the implementation of API controllers, API integrations, and functional scenarios.

## Implementation
The project is organised as a mono-repository of project modules with their own deployment workflow.

The application framworks are SpringBoot, NodeJs, Angular.

Communication is done synchronoulsy and asynchronously, and mainly involving REST APIs and messaging.

Observability involve metrics and tracing, and is based on OpenTelemetry.

The infrastructure as code applies to a local machine, a corporate cluster, or any cloud provider.

## Testing
The testing strategy includes every test taxonomy. Testing activity is present before, during, and after the realisation.

- Contract conformance tests (Controllers and Listeners layer)
- Functional acceptance tests (Application service layer)
- Integration tests (Application service layer, Adapters with external systems)
- Unit tests (Any internal component)
- Solution integration tests (System level, All or parts of the micro-service crowd)
- Non-functional acceptance tests (Security, load, stress, resilience)

Testing activity includes test-oriented thinking for better component testability and design, test scenarios identification, 
test writing, test-driven development, validation, and regression testing.