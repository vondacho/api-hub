package io.obya.api.onboarding.appl.usecase;

import io.cucumber.java.ParameterType;
import io.obya.api.onboarding.domain.model.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ParameterTypes {

    @ParameterType("DEPENDENCY_NOT_AVAILABLE|INSUFFICIENT_SCORING|REVISION_NOT_ALIGNED|REVISION_AUTO_INCREMENTED|MISSING_DATA|MALFORMED_VERSION|MALFORMED_REVISION|PROCESSING_FAILED")
    public Violation.Code violation(String code) {
        return Violation.Code.valueOf(code);
    }

    @ParameterType("REGISTERED|SCORED|VALID")
    public Status status(String status) {
        return Status.valueOf(status);
    }

    @ParameterType("OPENAPI_V30|ASYNCAPI_V30")
    public Contract.Version contract(String contract) {
        return Contract.Version.valueOf(contract);
    }

    @ParameterType("spec-\\d{3}")
    public SpecificationId specId(String specification) {
        return new SpecificationId(specification);
    }

    @ParameterType(".*\\.yaml")
    public URI uri(String filename) throws Exception {
        return UsecaseExamples.Sources.classpathOf(filename);
    }

    @ParameterType("v\\d{1}")
    public Version version(String version) {
        return Version.from(version);
    }

    @ParameterType("\\d{1}.\\d{1}.\\d{1}")
    public Revision revision(String revision) {
        return Revision.from(revision);
    }

    @ParameterType("\\d{4}-\\d{2}-\\d{2}")
    public LocalDate localDate(String date) {
        return LocalDate.parse(date); // ISO_LOCAL_DATE by default
    }

    @ParameterType("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")
    public LocalDateTime localDateTime(String dateTime) {
        return LocalDateTime.parse(dateTime); // ISO_LOCAL_DATE_TIME by default
    }
}
