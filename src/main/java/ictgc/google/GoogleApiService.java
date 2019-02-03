package ictgc.google;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import ictgc.ApplicationProperties;
import ictgc.domain.CalendarSynchronizationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Encapsulates required logic to obtain authorized Google Calendar APi Service.
 * Handles authorization and token exchange.
 */
@Service
@Slf4j
public class GoogleApiService {

    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    private static final String REDIRECT_URL_RESPONSE = "<html><head>" +
            "<title>OAuth 2.0 Authentication Token Received</title></head>" +
            "<body>All done, you may now close this window...</body></html>";

    private static final String CALLBACK_PATH = "/google-calendar-auth-callback";

    private final String authorizationRedirectUrl;
    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final DataStore<StoredCredential> credentialDataStore;
    private final GoogleAuthorizationCodeFlow authorizationFlow;
    private final ConcurrentMap<String, AuthorizationResponseCondition> authorizationResponseConditions =
            new ConcurrentHashMap<>();

    @Autowired
    public GoogleApiService(ApplicationProperties config)
            throws GeneralSecurityException, IOException {

        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.jsonFactory = JacksonFactory.getDefaultInstance();

        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(config.getAuthorizationStorageDirectory());
        this.credentialDataStore = StoredCredential.getDefaultDataStore(dataStoreFactory);

        this.authorizationFlow = createAuthorizationFlow(config);

        ApplicationProperties.AuthorizationServer authorizationServerConfig = config.getAuthorizationServer();
        int port = authorizationServerConfig.getListeningPort();

        this.authorizationRedirectUrl = authorizationServerConfig.getAuthorizationRedirectUrlBase() + CALLBACK_PATH;

        Server server = new Server(port);
        server.addHandler(new CallbackHandler());
        try {
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Loads client secret and initiates thread-safe authorization flow with offline access.
     */
    @Nonnull
    private GoogleAuthorizationCodeFlow createAuthorizationFlow(ApplicationProperties config)
            throws IOException {

        String clientSecretJson = IOUtils.toString(
                config.getGoogleClientSecretsFile().toURI(), StandardCharsets.UTF_8);

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(jsonFactory, new StringReader(clientSecretJson));

        return new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, SCOPES)
                .setCredentialDataStore(credentialDataStore)
                .setAccessType("offline")
                .build();
    }

    /**
     * Authorizes application for provided user and creates Calendar Service.
     * If there are no stored credentials, generates a URL to be provided to user in order to navigate
     * for authorization, prints the URL to log and causes current thread to wait until user navigates to the URL.
     * As soon as user authorizes the application via provided URL, the thread is woken up and continues execution
     * by constructing Google Calendar API Service.
     *
     * @param userId    ID of user to authorize application by.
     * @param userEmail email of user to authorize application by.
     * @return Calendar Service for user.
     */
    @Nonnull
    public com.google.api.services.calendar.Calendar getCalendarService(String userId, String userEmail) {
        Credential credential = authorize(userId, userEmail);
        return new com.google.api.services.calendar.Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("ical-to-google-calendar")
                .build();
    }

    public void resetCredentials(String userId) {
        try {
            credentialDataStore.delete(userId);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Authorizes the application and returns credentials (either previously stored or newly received).
     * If there are no stored credentials, generates a URL to be provided to user in order to navigate
     * for authorization, prints the URL to log and causes current thread to wait until user navigates to the URL.
     * As soon as user authorizes the application via provided URL, the thread is woken up and continues execution
     * by returning new credentials.
     *
     * @param userId    ID of user to get authorization from.
     * @param userEmail email of user to get authorization from.
     * @return credentials given by user.
     */
    @Nonnull
    private Credential authorize(String userId, String userEmail) {
        try {
            Credential credential = authorizationFlow.loadCredential(userId);
            if (credential != null
                    && (credential.getRefreshToken() != null || credential.getExpiresInSeconds() > 60)) {
                return credential;
            }

            String conditionId = UUID.randomUUID().toString();
            AuthorizationResponseCondition authorizationResponseCondition = new AuthorizationResponseCondition();
            authorizationResponseConditions.putIfAbsent(conditionId, authorizationResponseCondition);

            AuthorizationCodeRequestUrl authorizationUrl = authorizationFlow.newAuthorizationUrl()
                    .setRedirectUri(authorizationRedirectUrl)
                    .setState(conditionId);

            log.warn("Please open the following address in your browser:");
            log.warn("{}", authorizationUrl);

            authorizationResponseCondition.waitForAuthorizationCode();

            if (authorizationResponseCondition.getError() != null) {
                throw new CalendarSynchronizationException("Authorization error: " +
                        authorizationResponseCondition.getError());
            }

            TokenResponse tokenResponse = authorizationFlow.newTokenRequest(authorizationResponseCondition.getCode())
                    .setRedirectUri(authorizationRedirectUrl)
                    .execute();

            return authorizationFlow.createAndStoreCredential(tokenResponse, userId);
        } catch (IOException e) {
            throw new CalendarSynchronizationException(e);
        }
    }

    /**
     * Handles HTTP requests. If callback url is requested,
     * parses code, error and state and notifies waiting thread.
     */
    private class CallbackHandler extends AbstractHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                throws IOException {

            if (!CALLBACK_PATH.equals(target)) {
                return;
            }

            writeRedirectUrlHtml(response);
            response.flushBuffer();
            ((Request) request).setHandled(true);

            String error = request.getParameter("error");
            String code = request.getParameter("code");
            String state = request.getParameter("state");

            AuthorizationResponseCondition responseCondition = Objects.requireNonNull(
                    authorizationResponseConditions.remove(state));
            responseCondition.onAuthorizationResponse(code, error);
        }

        private void writeRedirectUrlHtml(HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");
            PrintWriter doc = response.getWriter();
            doc.println(REDIRECT_URL_RESPONSE);
            doc.flush();
        }
    }

    /**
     * {@link Condition} that waits until code and error are provided.
     */
    private static class AuthorizationResponseCondition {

        private final Lock lock = new ReentrantLock();
        private final Condition gotAuthorizationResponse = lock.newCondition();

        @Getter
        private String code;

        @Getter
        private String error;

        /**
         * Causes current thread to wait until code and error are provided.
         */
        public void waitForAuthorizationCode() throws IOException {
            lock.lock();
            try {
                while (code == null && error == null) {
                    gotAuthorizationResponse.awaitUninterruptibly();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Stores code and error and wakes up waiting thread.
         */
        public void onAuthorizationResponse(String code, String error) {
            lock.lock();
            try {
                this.code = code;
                this.error = error;

                gotAuthorizationResponse.signal();
            } finally {
                lock.unlock();
            }
        }
    }

}
