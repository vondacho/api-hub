---
name: test-fixtures
description: Conventions for test fixture values in api-onboarding. Every fixture constant, sample value, or example object used by a Java test MUST be declared in the `DomainExamples` or `UsecaseExamples` interface and static-imported — never declared locally in the test class. Load this BEFORE writing or editing any `*Test.java`, Cucumber step definition, or `*Examples.java` file under `api-onboarding/src/test/java`, and whenever a test needs a URI, a Scorecard/Score, a Specification, a Contract, a Revision/Version, or a prepared workflow `State`.
---

# Test fixture constants

## The rule

Test classes contain **behaviour**, not **data**. Any value a test needs as input or
expectation lives in one of the two example interfaces and is pulled in by static import.

```
api-onboarding/src/test/java/io/obya/api/onboarding/domain/model/DomainExamples.java
api-onboarding/src/test/java/io/obya/api/onboarding/appl/usecase/UsecaseExamples.java
```

A test class must not declare its own fixture constants. This is wrong:

```java
class OverlayerTest {
    private static final URI ORIGINAL_SOURCE = URI.create("file:///candidate.openapi.yaml");  // ✗
    private static final URI OVERLAY = URI.create("file:///overlay.yaml");                     // ✗
}
```

This is right:

```java
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.anySource;
import static io.obya.api.onboarding.domain.model.DomainExamples.Contracts.openApiV30;
import static io.obya.api.onboarding.domain.model.DomainExamples.Scores.acceptable;

class ScorerTest {
    // ...
    Try<State> result = score(delegate, new State().source(anySource.get()).contract(openApiV30.get()));
}
```

## Which interface

Pick by the layer the value belongs to, matching the hexagonal boundary — not by which
test happens to need it first.

| Interface | Package | Holds |
|---|---|---|
| `DomainExamples` | `io.obya.api.onboarding.domain.model` | Pure domain values: `Specification`, `SpecificationId`, `Contract`, `Version`, `Revision`, `Score`, `Scorecard`, `Metadata`, `Info`, `Violation` |
| `UsecaseExamples` | `io.obya.api.onboarding.appl.usecase` | Application-layer values: candidate/overlay `URI`s, resource bodies, and prepared `Try<State>` workflow states at each `Status` |

`UsecaseExamples` may reference `DomainExamples` (e.g. `States.candidateScored` composes
`DomainExamples.Scores.scorecard`). **Never the reverse** — `DomainExamples` stays free of
`appl` types, mirroring the production dependency direction.

## How to declare a fixture

Group related fixtures in a nested marker interface (`Specifications`, `Contracts`,
`Scores`, `Sources`, `States`). Add a new nested interface rather than flattening
unrelated values together.

- **Object fixtures → `Supplier<T>`**, so each test gets a fresh instance and nothing
  leaks between tests:
  ```java
  Supplier<Contract> openApiV30 = () -> Contract.from(Contract.Version.OPENAPI_V30);
  Supplier<Scorecard> acceptable = () -> Scorecard.globalOf(acceptableEvaluation);
  ```
- **Primitives / immutable scalars → plain interface fields** (implicitly
  `public static final`):
  ```java
  int acceptableEvaluation = Score.Grade.A.min;
  int tooLowEvaluation = Score.Grade.D.min;
  ```
- **Parameterised fixtures → a `Function`/`BiFunction` field, or a `static` factory method**
  when there are several arguments:
  ```java
  Function<Integer, Scorecard> fundationalCompliance = fc ->
          new Scorecard(new Score(fc), Map.of(Scorecard.Dimension.FC, new Score(fc)));

  static Specification specificationOf(SpecificationId id, String name, String productName,
                                       Version version, Revision revision, String body) { ... }
  ```
- **Naming**: lowerCamelCase, named for the *meaning* of the value, not its literal
  (`acceptable`, `tooLow`, `anySource`, `candidateScored`) — so a test reads as a sentence
  and the literal can change without renaming.
- **Aliases** are encouraged when a test only cares about "some valid value":
  ```java
  Supplier<URI> validCandidateUri = () -> uriOf("oas/valid_candidate.openapi.yaml");
  Supplier<URI> anySource = validCandidateUri;
  ```

## How to consume a fixture

- Static-import the **leaf field**, not the enclosing interface:
  `import static ...DomainExamples.Scores.acceptable;` — not `...DomainExamples.Scores;`.
- Do not `implements DomainExamples` on the test class; static import keeps the
  dependency explicit and visible at the top of the file.
- Call the supplier at the point of use: `acceptable.get()`.

## Procedure when a test needs a value that does not exist yet

1. Look for an existing fixture that already carries the meaning — reuse or alias it
   before adding anything.
2. Decide the layer (domain vs. usecase) from the table above.
3. Add it to the matching nested interface, in the shape prescribed above.
4. Static-import it into the test.
5. If the new fixture generalises a local constant already sitting in another test class,
   move that one out too and update its test — the interfaces are the single source of
   truth for fixture data.

## Boundaries

- Values that are *the subject under test* rather than fixture data (e.g. a deliberately
  malformed string constructed inline to prove a parser rejects it) may stay local, but
  prefer a named `Examples` entry as soon as more than one test needs it.
- Mockito mocks, stubs, and scripted `thenAnswer` behaviour are **test doubles, not
  fixtures** — they stay in the test class. Only the *data* they return comes from the
  example interfaces.
- Cucumber step definitions follow the same rule: `RegistrationCucumberSteps` takes its
  data from `UsecaseExamples` / `DomainExamples`.
