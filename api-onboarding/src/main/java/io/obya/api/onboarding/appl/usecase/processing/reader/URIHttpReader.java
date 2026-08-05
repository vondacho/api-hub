package io.obya.api.onboarding.appl.usecase.processing.reader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class URIHttpReader implements URIReader {

    @Override
    public boolean canRead(URI uri) {
        return uri.getScheme().equals("http") || uri.getScheme().equals("https");
    }

    @Override
    public String firstLineOnly(URI uri) throws IOException {
        return allInOne(uri).lines().findFirst().orElse("");
    }

    @Override
    public String allInOne(URI uri) throws IOException {
        validateScheme(uri);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private void validateScheme(URI uri) {
        if (!canRead(uri)) {
            throw new IllegalArgumentException("Not a valid URI, scheme must be http(s): " + uri);
        }
    }
}
