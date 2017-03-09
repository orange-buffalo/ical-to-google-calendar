package ictgc.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
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
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GoogleApiService {

    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    private final AuthorizationAppFactory authorizationAppFactory;
    private final ApplicationProperties config;
    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final DataStore<StoredCredential> credentialDataStore;

    @Autowired
    public GoogleApiService(AuthorizationAppFactory authorizationAppFactory, ApplicationProperties config)
            throws GeneralSecurityException, IOException {

        this.authorizationAppFactory = authorizationAppFactory;
        this.config = config;
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.jsonFactory = JacksonFactory.getDefaultInstance();

        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(config.getAuthorizationStorageDirectory());
        this.credentialDataStore = StoredCredential.getDefaultDataStore(dataStoreFactory);
    }

    public com.google.api.services.calendar.Calendar getCalendarService(String userId, String userEmail) {
        try {
            Credential credential = authorize(userId);
            return new com.google.api.services.calendar.Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("ical-to-google-calendar")
                    .build();
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void resetCredentials(String userId) {
        try {
            credentialDataStore.delete(userId);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Credential authorize(String userId) throws IOException {
        String clientSecretJson = org.apache.commons.io.IOUtils.toString(
                config.getGoogleClientSecretsFile().toURI(), StandardCharsets.UTF_8);

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(jsonFactory, new StringReader(clientSecretJson));

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, SCOPES)
                        .setCredentialDataStore(credentialDataStore)
                        .setAccessType("offline")
                        .build();

        return authorizationAppFactory.createAuthorizationApp(flow).authorize(userId);
    }

}
