package ictgc.google;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import ictgc.domain.CalendarSynchronizationException;
import java.io.IOException;

/**
 * Implementation of callback without any action on success and which throws
 * exception on failure.
 */
public class JsonBatchErrorCallback<T> extends JsonBatchCallback<T> {

    @Override
    public void onSuccess(T t, HttpHeaders responseHeaders) throws IOException {
        // no op
    }

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        throw new CalendarSynchronizationException("Error while processing request: " + e.toPrettyString());
    }
}
