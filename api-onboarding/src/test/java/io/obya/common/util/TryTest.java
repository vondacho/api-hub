package io.obya.common.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TryTest {

   // ------------------------------------------------------------------ factories

   @Nested class Factories {

      @Test void ofReturnsSuccessWhenSupplierSucceeds() {
         Try<String> t = Try.of(() -> "hello");
         assertTrue(t.isSuccess());
         assertEquals(Optional.of("hello"), t.getValue());
      }

      @Test void ofReturnsFailureWhenSupplierThrows() {
         Try<String> t = Try.of(() -> { throw new RuntimeException("boom"); });
         assertTrue(t.isFailure());
         assertEquals(1, t.getExceptions().size());
         assertEquals("boom", t.getExceptions().get(0).getMessage());
      }

      @Test void ofReturnsFailureWhenSupplierReturnsNull() {
         Try<String> t = Try.of(() -> null);
         assertTrue(t.isFailure());
      }

      @Test void ofOptionalUnwrapsPresent() {
         Try<String> t = Try.ofOptional(() -> Optional.of("val"));
         assertTrue(t.isSuccess());
      }

      @Test void ofOptionalReturnsFailureOnEmpty() {
         Try<String> t = Try.ofOptional(Optional::empty);
         assertTrue(t.isFailure());
         assertTrue(t.getExceptions().isEmpty());
      }

      @Test void successFactory() {
         assertTrue(Try.success("x").isSuccess());
      }

      @Test void failureFactoryWithSingleException() {
         Try<String> t = Try.failure(new IllegalStateException("bad"));
         assertTrue(t.isFailure());
         assertEquals(1, t.getExceptions().size());
      }

      @Test void failureFactoryWithList() {
         Try<String> t = Try.failure(List.of(new RuntimeException("a"), new RuntimeException("b")));
         assertEquals(2, t.getExceptions().size());
      }
   }

   // ------------------------------------------------------------------ map

   @Nested class Map {

      @Test void mapTransformsValueOnSuccess() {
         Try<Integer> t = Try.success("hello").map(String::length);
         assertTrue(t.isSuccess());
         assertEquals(Optional.of(5), t.getValue());
      }

      @Test void mapAccumulatesExceptionAndBecomesFailure() {
         Try<Integer> t = Try.success("hello").map(s -> { throw new RuntimeException("oops"); });
         assertTrue(t.isFailure());
         assertEquals(1, t.getExceptions().size());
      }

      @Test void mapOnFailurePropagatesToNewType() {
         Try<Integer> t = Try.<String>failure(new RuntimeException("x")).map(String::length);
         assertTrue(t.isFailure());
         assertEquals(1, t.getExceptions().size());
      }

      @Test void mapPreservesPartialState() {
         Try<String> partial = new Try.Partial<>("hello", List.of(new RuntimeException("warn")));
         Try<Integer> mapped = partial.map(String::length);
         assertTrue(mapped.isPartial());
         assertEquals(Optional.of(5), mapped.getValue());
         assertEquals(1, mapped.getExceptions().size());
      }

      @Test void mapWithNullResultBecomesFailure() {
         Try<String> t = Try.success("x").map(s -> null);
         assertTrue(t.isFailure());
      }
   }

   // ------------------------------------------------------------------ flatMap

   @Nested class FlatMap {

      @Test void flatMapMergesSuccesses() {
         Try<String> t = Try.success(42).flatMap(n -> Try.success("n=" + n));
         assertTrue(t.isSuccess());
         assertEquals("n=42", t.getValue().get());
      }

      @Test void flatMapMergesExceptionsFromBothSides() {
         RuntimeException outer = new RuntimeException("outer");
         RuntimeException inner = new RuntimeException("inner");

         Try<Integer> partial = new Try.Partial<>(42, List.of(outer));
         Try<String> result  = partial.flatMap(n -> new Try.Partial<>("n=" + n, List.of(inner)));

         assertTrue(result.isPartial());
         assertEquals(2, result.getExceptions().size());
      }

      @Test void flatMapPropagatesFailure() {
         Try<String> t = Try.<Integer>failure(new RuntimeException("x"))
                 .flatMap(n -> Try.success("n=" + n));
         assertTrue(t.isFailure());
      }

      @Test void flatMapWithInnerFailureAccumulatesExceptions() {
         RuntimeException e1 = new RuntimeException("first");
         RuntimeException e2 = new RuntimeException("second");

         Try<String> t = new Try.Partial<>(1, List.of(e1))
                 .flatMap(n -> Try.failure(e2));
         assertTrue(t.isFailure());
         assertEquals(2, t.getExceptions().size());
      }
   }

   // ------------------------------------------------------------------ filter

   @Nested class Filter {

      @Test void filterKeepsValueWhenPredicateHolds() {
         Try<String> t = Try.success("hello")
                 .filter(s -> s.length() > 3, _ -> new IllegalArgumentException("too short"));
         assertTrue(t.isSuccess());
      }

      @Test void filterStrictRemovesValueWhenPredicateFails() {
         Try<String> t = Try.success("hi")
                 .filter(s -> s.length() > 3, _ -> new IllegalArgumentException("too short"), true);
         assertTrue(t.isFailure());
         assertInstanceOf(IllegalArgumentException.class, t.getExceptions().get(0));
      }

      @Test void filterSwitchesToPartialOnExceptionState() {
         Try<String> t = Try.success("one")
                 .filter(s -> s.length() > 3, _ -> new IllegalArgumentException("too short"));
         assertTrue(t.isPartial());
         assertInstanceOf(IllegalArgumentException.class, t.getExceptions().get(0));
      }

      @Test void filterPreservesPartialState() {
         Try<String> t = new Try.Partial<>("one", List.of(new RuntimeException("warn")))
                 .filter(s -> s.length() > 3, _ -> new IllegalArgumentException("too short"));
         assertTrue(t.isPartial());
         assertInstanceOf(RuntimeException.class, t.getExceptions().get(0));
         assertInstanceOf(IllegalArgumentException.class, t.getExceptions().get(1));
      }

      @Test void filterOnFailureIsNoop() {
         RuntimeException existing = new RuntimeException("existing");
         Try<String> t = Try.<String>failure(existing)
                 .filter(s -> true, _ -> new RuntimeException("never"));
         assertEquals(1, t.getExceptions().size());
         assertSame(existing, t.getExceptions().get(0));
      }
   }

   // ------------------------------------------------------------------ recover

   @Nested class Recover {

      @Test void recoverRestoresValueFromFailure() {
         Try<String> t = Try.<String>failure(new RuntimeException("x"))
                 .recover(exs -> "fallback");
         assertTrue(t.isPartial());
         assertEquals("fallback", t.getValue().get());
         assertEquals(1, t.getExceptions().size());
      }

      @Test void recoverDoesNotTouchSuccess() {
         Try<String> t = Try.success("ok").recover(exs -> "fallback");
         assertTrue(t.isSuccess());
         assertEquals("ok", t.getValue().get());
      }

      @Test void recoverWithThrowingFunctionAccumulatesException() {
         Try<String> t = Try.<String>failure(new RuntimeException("original"))
                 .recover(exs -> { throw new RuntimeException("recovery failed"); });
         assertTrue(t.isFailure());
         assertEquals(2, t.getExceptions().size());
      }

      @Test void recoverWithFlatMapsIntoNewTry() {
         RuntimeException e = new RuntimeException("e");
         Try<String> t = Try.<String>failure(e)
                 .recoverWith(exs -> Try.success("recovered"));
         assertTrue(t.isSuccess());
         assertEquals("recovered", t.getValue().get());
      }
   }

   // ------------------------------------------------------------------ terminal

   @Nested class Terminal {

      @Test void getOrElseReturnsValueWhenPresent() {
         assertEquals("hello", Try.success("hello").getOrElse("default"));
      }

      @Test void getOrElseReturnsDefaultOnFailure() {
         assertEquals("default", Try.<String>failure(new RuntimeException()).getOrElse("default"));
      }

      @Test void getOrThrowThrowsOnFailure() {
         assertThrows(IllegalStateException.class,
                 () -> Try.<String>failure(new RuntimeException("x")).getOrThrow(() -> new IllegalStateException("no value")));
      }

      @Test void getOrThrowNoArgWrapsAllExceptions() {
         RuntimeException e1 = new RuntimeException("first");
         RuntimeException e2 = new RuntimeException("second");
         Try<String> t = Try.failure(List.of(e1, e2));

         RuntimeException thrown = assertThrows(RuntimeException.class, t::getOrThrow);
         assertSame(e1, thrown.getCause());
         assertEquals(1, thrown.getSuppressed().length);
         assertSame(e2, thrown.getSuppressed()[0]);
      }

      @Test void foldRoutesToCorrectBranch() {
         String success = Try.success("v").fold(v -> "ok:" + v, exs -> "fail", (v, exs) -> "partial");
         String failure = Try.<String>failure(new RuntimeException()).fold(v -> "ok", exs -> "fail", (v, exs) -> "partial");
         String partial = new Try.Partial<>("v", List.of(new RuntimeException())).fold(v -> "ok", exs -> "fail", (v, exs) -> "partial:" + v);

         assertEquals("ok:v",      success);
         assertEquals("fail",      failure);
         assertEquals("partial:v", partial);
      }

      @Test void ifSuccessCalledOnlyOnSuccess() {
         boolean[] called = {false};
         Try.success("x").ifSuccess(v -> called[0] = true);
         assertTrue(called[0]);
      }

      @Test void ifFailureCalledOnlyOnFailure() {
         boolean[] called = {false};
         Try.<String>failure(new RuntimeException()).ifFailure(exs -> called[0] = true);
         assertTrue(called[0]);
      }
   }

   // ------------------------------------------------------------------ integration

   @Nested class Integration {

      /** Simulates a validation pipeline that collects all failures. */
      @Test void validationPipelineCollectsAllErrors() {
         record User(String name, String email, int age) {}

         Try<User> result = Try.of(() -> new User("", "not-an-email", -1))
                 .filter(u -> !u.name().isBlank(),  _ -> new IllegalArgumentException("name is blank"))
                 .filter(u -> u.email().contains("@"), _ -> new IllegalArgumentException("invalid email"))
                 .filter(u -> u.age() >= 0,         _ -> new IllegalArgumentException("age is negative"));

         assertTrue(result.isPartial());
         assertEquals(3, result.getExceptions().size());
      }

      @Test void validationPipelineFailsAndCollectsAllErrors() {
         record User(String name, String email, int age) {}

         Try<User> result = Try.of(() -> new User("", "not-an-email", -1))
                 .filter(u -> !u.name().isBlank(),  _ -> new IllegalArgumentException("name is blank"), true)
                 .filter(u -> u.email().contains("@"), _ -> new IllegalArgumentException("invalid email"), true)
                 .filter(u -> u.age() >= 0,         _ -> new IllegalArgumentException("age is negative"), true);

         assertTrue(result.isFailure());
         assertEquals(1, result.getExceptions().size());
      }

      /** Simulates a fetch with partial degradation. */
      @Test void partialDegradation() {
         Try<List<String>> fetched = Try.of(() -> List.of("article-1", "article-2"));

         Try<List<String>> withWarning = fetched.flatMap(articles ->
                 new Try.Partial<>(articles, List.of(new RuntimeException("stale cache used"))));

         assertTrue(withWarning.isPartial());
         assertEquals(2, withWarning.getValue().get().size());
         assertEquals(1, withWarning.getExceptions().size());
      }

      /** Chained maps preserve accumulated exceptions through the pipeline. */
      @Test void exceptionsPreservedThroughChainedMaps() {
         Try<String> result = new Try.Partial<>(42, List.of(new RuntimeException("warn")))
                 .map(n -> n * 2)
                 .map(n -> "value=" + n);

         assertTrue(result.isPartial());
         assertEquals("value=84", result.getValue().get());
         assertEquals(1, result.getExceptions().size());
      }
   }
}

