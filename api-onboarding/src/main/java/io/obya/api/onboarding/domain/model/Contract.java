package io.obya.api.onboarding.domain.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public record Contract(Type type, Version version) {

    public static Contract from(Version version) {
        return new Contract(Type.findType(version), version);
    }

    public enum Type {
        OPENAPI(Pattern.compile(".*openapi.*"),
                Version.OPENAPI_V30,
                Version.OPENAPI_V31,
                Version.OPENAPI_V32),

        ASYNCAPI(Pattern.compile(".*asyncapi.*"),
                Version.ASYNCAPI_V20,
                Version.ASYNCAPI_V26,
                Version.ASYNCAPI_V30),

        GRAPHQLS(Pattern.compile(".*schema.*"), Version.GRAPHQLS),
        WSDL(Pattern.compile(".*<types>|<message>|<portType>|<binding>.*"), Version.WSDL_V11,  Version.WSDL_V20),
        OVERLAY(Pattern.compile(".*overlay.*"), Version.OVERLAY_V10, Version.OVERLAY_V11);

        public final Pattern pattern;
        public final Version[] versions;

        Type(Pattern pattern, Version...versions) {
            this.pattern = pattern;
            this.versions = versions;
        }

        public boolean matches(String text) {
            return pattern.matcher(text).matches();
        }

        public static Type findType(String text) {
            for (Type type : values()) {
                if (type.matches(text)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown contract type [%s]".formatted(text));
        }

        public static Type findType(Version version) {
            for (Type type : values()) {
                if (Arrays.asList(type.versions).contains(version)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("No contract type linked to [%s]".formatted(version));
        }

        public Optional<Version> findVersion(String text) {
            if (versions.length == 0) { return Optional.empty(); }
            for (Version version : versions) {
                if (version.matches(text)) {
                    return Optional.of(version);
                }
            }
            throw new IllegalArgumentException("Unknown contract version [%s]".formatted(text));
        }
    }

    public enum Version {
        OPENAPI_V30(Pattern.compile(".*3\\.0\\.\\d+.*")),
        OPENAPI_V31(Pattern.compile(".*3\\.1\\.0.*")),
        OPENAPI_V32(Pattern.compile(".*3\\.2\\.0.*")),
        ASYNCAPI_V20(Pattern.compile(".*2\\.0\\.0.*")),
        ASYNCAPI_V26(Pattern.compile(".*2\\.6\\.0.*")),
        ASYNCAPI_V30(Pattern.compile(".*3\\.0\\.0.*")),
        GRAPHQLS(Pattern.compile(".*")),
        WSDL_V11(Pattern.compile(".*1\\.1.*")),
        WSDL_V20(Pattern.compile(".*2\\.0.*")),
        OVERLAY_V10(Pattern.compile(".*1\\.0\\.0.*")),
        OVERLAY_V11(Pattern.compile(".*1\\.1\\.0.*"));

        public final Pattern pattern;

        Version(Pattern pattern) {
            this.pattern = pattern;
        }

        public boolean matches(String text) {
            return pattern.matcher(text).matches();
        }
    }

}
