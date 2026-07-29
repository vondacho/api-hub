package io.obya.common.util;

import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.*;

/**
 * A monad that carries an Optional value together with a list of accumulated
 * exceptions. Unlike a simple Either, Try never short-circuits: you can chain
 * operations and collect every failure before inspecting the result.
 *
 * <p>Three states:</p>
 * <ul>
 *   <li>{@code Success<T>}  - value present, no exceptions</li>
 *   <li>{@code Partial<T>}  - value present, but exceptions were collected along the way</li>
 *   <li>{@code Failure<T>}  - no value, one or more exceptions</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Try<String> result = Try.of(() -> fetchFromStrapi())
 *     .map(String::trim)
 *     .filter(s -> !s.isEmpty(), () -> new IllegalStateException("empty response"))
 *     .recover(ex -> "fallback");
 *
 * result.getValue();      // Optional<String>
 * result.getExceptions(); // List<Exception>
 * result.isSuccess();     // true only when value present AND no exceptions
 * }</pre>
 */
public sealed interface Try<T> permits Try.Success, Try.Partial, Try.Failure {

   // ------------------------------------------------------------------ factories

   static <T> Try<T> of(Supplier<T> supplier) {
      try {
         T value = supplier.get();
         return value != null ? new Success<>(value) : new Failure<>(List.of());
      } catch (Exception ex) {
         return new Failure<>(List.of(ex));
      }
   }

   static <T> Try<T> ofOptional(Supplier<Optional<T>> supplier) {
      try {
         Optional<T> opt = supplier.get();
         if (opt.isPresent()) return new Success<>(opt.get());
         return new Failure<>(List.of());
      } catch (Exception ex) {
         return new Failure<>(List.of(ex));
      }
   }

   static <T> Try<T> success(T value) {
      return new Success<>(value);
   }

   static <T> Try<T> failure(Exception ex) {
      return new Failure<>(List.of(ex));
   }

   static <T> Try<T> failure(List<Exception> exceptions) {
      return new Failure<>(List.copyOf(exceptions));
   }

   // ------------------------------------------------------------------ core API

   Optional<T> getValue();

   List<Exception> getExceptions();

   default boolean isSuccess() {
      return getValue().isPresent() && getExceptions().isEmpty();
   }

   default boolean isPartial() {
      return getValue().isPresent() && !getExceptions().isEmpty();
   }

   default boolean isFailure() {
      return getValue().isEmpty();
   }

   default boolean hasExceptions() {
      return !getExceptions().isEmpty();
   }

   // ------------------------------------------------------------------ map / flatMap

   /**
    * Transforms the value if present. Exceptions thrown inside the mapper are
    * accumulated; the result becomes a Failure if the mapper throws and the
    * current Try carries no value, or a Partial if it does.
    */
   default <U> Try<U> map(Function<T, U> mapper) {
      Optional<T> v = getValue();
      if (v.isEmpty()) {
         return new Failure<>(getExceptions());
      }
      try {
         U mapped = mapper.apply(v.get());
         if (mapped == null) return addException(new NullPointerException("mapper returned null"));
         return withValue(mapped);
      } catch (Exception ex) {
         return addException(ex);
      }
   }

   /**
    * Flat-maps over the value, merging exceptions from both Try instances.
    */
   default <U> Try<U> flatMap(Function<T, Try<U>> mapper) {
      Optional<T> v = getValue();
      if (v.isEmpty()) {
         return new Failure<>(getExceptions());
      }
      try {
         Try<U> inner = mapper.apply(v.get());
         List<Exception> merged = merge(getExceptions(), inner.getExceptions());
         Optional<U> innerVal = inner.getValue();
         return innerVal.<Try<U>>map(u -> merged.isEmpty() ? new Success<>(u) : new Partial<>(u, merged))
                 .orElseGet(() -> new Failure<>(merged));
      } catch (Exception ex) {
         return addException(ex);
      }
   }

   /**
    * Keeps the previous value degraded further on each failure; accumulates the
    * supplied exception and transitions to Partial (strict=false) or Failure (strict=true).
    */
   default Try<T> filter(Predicate<T> predicate, Supplier<? extends Exception> onViolation) {
      return filter(predicate, onViolation, false);
   }

   default Try<T> filter(Predicate<T> predicate, Supplier<? extends Exception> onViolation, boolean strict) {
      Optional<T> v = getValue();
      if (v.isEmpty()) return this;
      if (predicate.test(v.get())) return this;
      final Exception e = onViolation.get();
      return strict ? addException(e) : addException(e).withValue(v.get());
   }

   default Try<T> filter(Predicate<T> predicate, Function<T, ? extends Exception> onViolation) {
      return filter(predicate, onViolation, false);
   }

   default Try<T> filter(Predicate<T> predicate, Function<T, ? extends Exception> onViolation, boolean strict) {
      Optional<T> v = getValue();
      if (v.isEmpty()) return this;
      if (predicate.test(v.get())) return this;
      final Exception e = onViolation.apply(v.get());
      return strict ? addException(e) : addException(e).withValue(v.get());
   }

   // ------------------------------------------------------------------ recovery

   /**
    * Attempts to recover a missing value from the accumulated exceptions.
    * Exceptions are preserved for observability; the result is a Partial.
    * If recovery itself throws, that exception is added too.
    */
   default Try<T> recover(Function<List<Exception>, T> recovery) {
      if (getValue().isPresent()) return this;
      try {
         T recovered = recovery.apply(getExceptions());
         if (recovered == null) return this;
         return new Partial<>(recovered, getExceptions());
      } catch (Exception ex) {
         return new Failure<>(merge(getExceptions(), List.of(ex)));
      }
   }

   /**
    * Like {@code recover} but the recovery function may itself return a Try,
    * allowing further exception accumulation.
    */
   default Try<T> recoverWith(Function<List<Exception>, Try<T>> recovery) {
      if (getValue().isPresent()) return this;
      try {
         Try<T> inner = recovery.apply(getExceptions());
         List<Exception> merged = inner.getExceptions();
         Optional<T> innerVal = inner.getValue();
         return innerVal.map(val -> merged.isEmpty() ? new Success<>(val) : new Partial<>(val, merged))
                 .orElseGet(() -> new Failure<>(merged));
      } catch (Exception ex) {
         return new Failure<>(merge(getExceptions(), List.of(ex)));
      }
   }

   /**
    * Like {@code recover} but the recovery function may itself return a Try incuding a flatmap,
    * allowing further exception accumulation.
    */
   default <U> Try<U> recoverWithOther(Function<List<Exception>, Try<U>> recovery) {
      if (getValue().isPresent()) return this.flatMap(_ -> recovery.apply(getExceptions()));
      try {
         Try<U> inner = recovery.apply(getExceptions());
         List<Exception> merged = inner.getExceptions();
         Optional<U> innerVal = inner.getValue();
         return innerVal.map(val -> merged.isEmpty() ? new Success<>(val) : new Partial<>(val, merged))
                 .orElseGet(() -> new Failure<>(merged));
      } catch (Exception ex) {
         return new Failure<>(merge(getExceptions(), List.of(ex)));
      }
   }

   // ------------------------------------------------------------------ terminal

   default T getOrElse(T defaultValue) {
      return getValue().orElse(defaultValue);
   }

   default T getOrElseGet(Supplier<T> supplier) {
      return getValue().orElseGet(supplier);
   }

   default <X extends Throwable> T getOrThrow(Supplier<X> exceptionSupplier) throws X {
      return getValue().orElseThrow(exceptionSupplier);
   }

   /** Throws the first accumulated exception wrapped in a RuntimeException. */
   default T getOrThrow() {
      if (getValue().isPresent()) return getValue().get();
      List<Exception> exs = getExceptions();
      if (exs.isEmpty()) throw new IllegalStateException("Try has no value and no exceptions");
      RuntimeException wrapper = new RuntimeException(
              "Try failed with " + exs.size() + " exception(s)", exs.get(0));
      exs.stream().skip(1).forEach(wrapper::addSuppressed);
      throw wrapper;
   }

   default void ifSuccess(Consumer<T> consumer) {
      if (isSuccess()) getValue().ifPresent(consumer);
   }

   default void ifFailure(Consumer<List<Exception>> consumer) {
      if (isFailure()) consumer.accept(getExceptions());
   }

   default void ifPartial(Consumer<T> valueConsumer, Consumer<List<Exception>> exConsumer) {
      if (isPartial()) {
         getValue().ifPresent(valueConsumer);
         exConsumer.accept(getExceptions());
      }
   }

   /** Folds all three states into a single result. */
   default <R> R fold(
           Function<T, R> onSuccess,
           Function<List<Exception>, R> onFailure,
           BiFunction<T, List<Exception>, R> onPartial
   ) {
      if (isSuccess()) return onSuccess.apply(getValue().get());
      if (isFailure()) return onFailure.apply(getExceptions());
      return onPartial.apply(getValue().get(), getExceptions());
   }

   // ------------------------------------------------------------------ records

   record Success<T>(T value) implements Try<T> {
      public Success {
         Objects.requireNonNull(value, "Success value must not be null");
      }
      @Override public Optional<T>     getValue()      { return Optional.of(value); }
      @Override public List<Exception> getExceptions() { return List.of(); }
      @Override public @NonNull String toString() { return "Success[" + value + "]"; }
   }

   record Partial<T>(T value, List<Exception> exceptions) implements Try<T> {
      public Partial {
         Objects.requireNonNull(value, "Partial value must not be null");
         exceptions = List.copyOf(exceptions);
      }
      @Override public Optional<T>     getValue()      { return Optional.of(value); }
      @Override public List<Exception> getExceptions() { return exceptions; }
      @Override public @NonNull String toString() {
         return "Partial[value=" + value + ", exceptions=" + exceptions.size() + "]";
      }
   }

   record Failure<T>(List<Exception> exceptions) implements Try<T> {
      public Failure { exceptions = List.copyOf(exceptions); }
      @Override public Optional<T>     getValue()      { return Optional.empty(); }
      @Override public List<Exception> getExceptions() { return exceptions; }
      @Override public @NonNull String toString() {
         return "Failure[exceptions=" + exceptions.size() + "]";
      }
   }

   // ------------------------------------------------------------------ internals

   private <U> Try<U> withValue(U newValue) {
      return getExceptions().isEmpty()
              ? new Success<>(newValue)
              : new Partial<>(newValue, getExceptions());
   }

   private <U> Try<U> addException(Exception ex) {
      List<Exception> all = merge(getExceptions(), List.of(ex));
      return new Failure<>(all);
   }

   private static List<Exception> merge(List<Exception> a, List<Exception> b) {
      if (a.isEmpty()) return b;
      if (b.isEmpty()) return a;
      List<Exception> merged = new ArrayList<>(a.size() + b.size());
      merged.addAll(a);
      merged.addAll(b);
      return Collections.unmodifiableList(merged);
   }
}

