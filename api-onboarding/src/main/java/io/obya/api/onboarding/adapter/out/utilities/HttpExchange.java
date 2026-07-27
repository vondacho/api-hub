package io.obya.api.onboarding.adapter.out.utilities;

import io.obya.common.util.Try;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.function.Function;
import java.util.function.Supplier;

public class HttpExchange {

    private HttpExchange() {
    }

    public static <T,R> Try<R> execute(Supplier<ResponseEntity<T>> request, Function<T, Try<R>> packaging) {
        try {
            ResponseEntity<T> response = request.get();

            if (response.getStatusCode().is5xxServerError()) {
                throw new HttpServerErrorException(response.getStatusCode());
            }
            if (response.getStatusCode().is4xxClientError()) {
                throw new HttpClientErrorException(response.getStatusCode());
            }
            return packaging.apply(response.getBody());

        } catch (ResourceAccessException e) {
            throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RestClientException e) {
            if (e.getCause() != null) {
                throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, e.getCause().getMessage());
            }
            throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
