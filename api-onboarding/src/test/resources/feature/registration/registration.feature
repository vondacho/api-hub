Feature: API specification onboarding
  As the API portal
  I want to validate and manage API specifications
  So that only valid and scored specifications enter the registry

  Background:
    Given the registry holds no prior specification

  Scenario: A valid and scored OpenAPI candidate is registered
    When the candidate oas/valid_candidate.openapi.yaml is submitted
    Then the onboarding succeeds
    And the specification id is spec-123
    And the specification version is v1
    And the contract is OPENAPI_V30
    And a scorecard is assigned with global score of 74
    And the specification status is REGISTERED
    And the specification body contains "x-score"
    And registered spec-123 named "petstore" in "platform" at v1 1.0.0

  Scenario: A valid and scored AsyncAPI candidate is registered
    When the candidate aas/valid_candidate.asyncapi.yaml is submitted
    Then the onboarding succeeds
    And the specification status is REGISTERED
    And the specification id is spec-123
    And the specification version is v1
    And the contract is ASYNCAPI_V30
    And a scorecard is assigned with global score of 74
    And registered spec-123 named "notification" in "platform" at v1 1.0.0

  Scenario: An unexistent candidate is not onboarded
    When the candidate not_found.yaml is submitted
    Then the onboarding fails
    And a violation PROCESSING_FAILED is reported

  Scenario: A candidate missing required info is not onboarded
    When the candidate oas/invalid_candidate_missing_required_info.openapi.yaml is submitted
    Then the onboarding fails
    And a violation MISSING_DATA is reported

  Scenario: A candidate having a malformed version is not onboarded
    When the candidate oas/invalid_candidate_malformed_version.openapi.yaml is submitted
    Then the onboarding fails
    And a violation MALFORMED_VERSION is reported

  Scenario: A candidate missing required metadata is not onboarded
    When the candidate oas/invalid_candidate_missing_required_metadata.openapi.yaml is submitted
    Then the onboarding fails
    And a violation MISSING_DATA is reported

  Scenario: A candidate having a malformed revision is not onboarded
    When the candidate oas/invalid_candidate_malformed_revision.openapi.yaml is submitted
    Then the onboarding fails
    And a violation MALFORMED_REVISION is reported

  Scenario: A candidate missing non-mandatory info is onboarded
    When the candidate oas/valid_candidate_missing_non_required_info.openapi.yaml is submitted
    Then the onboarding succeeds
    And the specification status is REGISTERED
    And no violation reported

  Scenario: A candidate missing non-mandatory metadata is onboarded
    When the candidate oas/valid_candidate_missing_non_required_metadata.openapi.yaml is submitted
    Then the onboarding succeeds
    And the specification status is REGISTERED
    And no violation reported

  Scenario: A valid candidate scored under the minimal threshold is not onboarded
    When the candidate oas/valid_candidate_low_score.openapi.yaml is submitted
    Then the onboarding fails
    And a violation INSUFFICIENT_SCORING is reported

  Scenario: A valid candidate is not registered when the scorer is not available
    Given the scorer cannot score specifications
    When the candidate oas/valid_candidate.openapi.yaml is submitted
    Then the onboarding succeeds
    And the specification status is VALID
    And no specification id is assigned
    And no scorecard is assigned
    And a warning DEPENDENCY_NOT_AVAILABLE is reported

  Scenario: A valid candidate is not registered when the registry is not available
    Given the registry cannot register specifications
    When the candidate oas/valid_candidate.openapi.yaml is submitted
    Then the onboarding succeeds
    And the specification status is SCORED
    And no specification id is assigned
    And a warning DEPENDENCY_NOT_AVAILABLE is reported

  Scenario: An onboarded candidate gets a revision
    When the candidate oas/valid_candidate.openapi.yaml is submitted on 2026-07-01
    Then the onboarding succeeds
    And the specification id is spec-123
    And the specification revision is 1.0.0
    And the audit contains "candidate onboarded" on 2026-07-01
    And registered spec-123 named "petstore" in "platform" at v1 1.0.0

  Scenario: An update on a registered specification creates a new revision
    Given the registry holds spec-456 named "petstore" in "platform" at v1 1.0.0
    When the candidate oas/valid_candidate.openapi.yaml is submitted on 2026-07-01
    Then the onboarding succeeds
    And the specification status is REGISTERED
    And the specification id is spec-123
    And the specification version is v1
    And the specification revision is 1.0.1
    And the audit contains "candidate onboarded" on 2026-07-01
    And registered spec-123 named "petstore" in "platform" at v1 1.0.1

  Scenario: A candidate having a next revision is onboarded
    Given the registry holds spec-456 named "petstore" in "platform" at v1 1.0.0
    When the candidate oas/valid_candidate_v1_1_0_1.openapi.yaml is submitted on 2026-07-01
    Then the onboarding succeeds
    And the specification status is REGISTERED
    And the specification id is spec-123
    And the specification version is v1
    And the specification revision is 1.0.1
    And the audit contains "candidate onboarded" on 2026-07-01
    And registered spec-123 named "petstore" in "platform" at v1 1.0.1

  Scenario: A candidate having a previous revision is onboarded
    Given the registry holds spec-123 named "petstore" in "platform" at v1 1.0.1
    When the candidate oas/valid_candidate_v1_1_0_0.openapi.yaml is submitted on 2026-07-01
    Then the onboarding succeeds
    And the specification status is REGISTERED
    And the specification version is v1
    And the specification revision is 1.0.2
    And the audit contains "candidate onboarded" on 2026-07-01
    And registered spec-123 named "petstore" in "platform" at v1 1.0.2
    And a warning REVISION_AUTO_INCREMENTED is reported

  Scenario: A candidate having a disaligned revision is onboarded
    When the candidate oas/valid_candidate_v1_0_0_1.openapi.yaml is submitted on 2026-07-01
    Then the onboarding succeeds
    And the specification status is REGISTERED
    And the specification version is v1
    And the specification revision is 1.0.0
    And the audit contains "candidate onboarded" on 2026-07-01
    And registered spec-123 named "petstore" in "platform" at v1 1.0.0
    And a warning REVISION_NOT_ALIGNED is reported

  Scenario: To actualise the score of an existing revision
    Given the registry holds spec-456 named "petstore" in "platform" at v1 1.0.0
    When scoring spec-456 on 2026-07-01
    Then the scoring succeeds
    And the specification id is spec-456
    And the specification status is REGISTERED
    And the specification version is v1
    And the specification revision is 1.0.0
    And the specification body contains "x-score: 74"
    And the audit contains "specification scored" on 2026-07-01
    And registered spec-456 named "petstore" in "platform" at v1 1.0.0

  Scenario: To associate a running component with an existing revision
    Given the registry holds spec-456 named "petstore" in "platform" at v1 1.0.0
    When associating spec-456 with component "X" 1.0.0 on 2026-07-01
    Then the implementation succeeds
    And the specification id is spec-456
    And the specification status is REGISTERED
    And the specification version is v1
    And the specification revision is 1.0.0
    And the specification body contains "x-component-name: X"
    And the specification body contains "x-component-revision: 1.0.0"
    And the audit contains "specification implemented" on 2026-07-01
    And registered spec-456 named "petstore" in "platform" at v1 1.0.0

  Scenario: Updating an existing revision using an overlay registers a new revision
    Given the registry holds spec-456 named "petstore" in "platform" at v1 1.0.0
    When applying overlay oai/test.overlay.yaml to spec-456 on 2026-07-01
    Then the overlaying succeeds
    And the specification id is spec-123
    And the specification status is REGISTERED
    And the specification version is v1
    And the specification revision is 1.0.1
    And the specification body contains "x-test: test"
    And the audit contains "specification overlaid" on 2026-07-01
    And registered spec-123 named "petstore" in "platform" at v1 1.0.1
