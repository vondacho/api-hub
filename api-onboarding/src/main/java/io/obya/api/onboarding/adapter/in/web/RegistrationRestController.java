package io.obya.api.onboarding.adapter.in.web;

import io.obya.api.onboarding.adapter.in.web.model.CandidateProcessed;
import io.obya.api.onboarding.adapter.in.web.model.OverlayApplied;
import io.obya.api.onboarding.adapter.in.web.model.OverlayCandidate;
import io.obya.api.onboarding.adapter.in.web.model.ScoreSummary;
import io.obya.api.onboarding.appl.usecase.OnBoardingException;
import io.obya.api.onboarding.adapter.in.web.model.Candidate;
import io.obya.api.onboarding.appl.usecase.RegistrationService;
import io.obya.api.onboarding.domain.model.Status;
import io.obya.api.onboarding.domain.model.Violation;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
@RestController
public class RegistrationRestController implements RegistrationApi {

    private final RegistrationService registrationService;

    public RegistrationRestController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Override
    public ResponseEntity<CandidateProcessed> submit(Candidate candidate) {
        Try<State> state =  registrationService.submit(candidate.source());
        return state.map(s -> ResponseEntity
            .status(s.status() == Status.REGISTERED ? CREATED : OK)
            .body(CandidateProcessed.from(s, Violation.from(state.getExceptions()))))
                .getOrThrow(() -> new OnBoardingException(Violation.from(state.getExceptions())));
    }

    @Override
    public ResponseEntity<ScoreSummary> score(SpecificationId id) {
        Try<State> state =  registrationService.score(id);
        return state.map(s -> ResponseEntity
                        .status(OK)
                        .body(ScoreSummary.from(s.score(), Violation.from(state.getExceptions()))))
                .getOrThrow(() -> new OnBoardingException(Violation.from(state.getExceptions())));
    }

    @Override
    public void implement(SpecificationId id, Component component) {
        Try<State> state =  registrationService.implement(id, component);
        state.map(_ -> ResponseEntity.status(NO_CONTENT).build())
                .getOrThrow(() -> new OnBoardingException(Violation.from(state.getExceptions())));
    }

    @Override
    public ResponseEntity<OverlayApplied> overlay(SpecificationId id, OverlayCandidate overlay) {
        Try<State> state =  registrationService.overlay(id, overlay.source());
        return state.map(s -> ResponseEntity.status(CREATED).body(new OverlayApplied(s.id().id())))
                .getOrThrow(() -> new OnBoardingException(Violation.from(state.getExceptions())));
    }
}
