package ictgc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ical-to-google-calendar")
@Component
@Getter
@Setter
@Validated
public class ApplicationProperties {

    @NotNull
    @Valid
    private List<User> users = new ArrayList<>();

    @NotNull
    @Valid
    private List<Flow> flows = new ArrayList<>();

    @NotNull
    @Valid
    private AuthorizationServer authorizationServer;

    @NotNull
    private File authorizationStorageDirectory;

    @NotNull
    private File googleClientSecretsFile;

    @Getter
    @Setter
    public static class User {
        @NotNull
        private String id;

        @NotNull
        private String email;
    }

    @Getter
    @Setter
    public static class Flow {
        @NotNull
        private String userId;

        @NotNull
        private String iCalUrl;

        @NotNull
        private String googleCalendarName;

        @NotNull
        private String defaultICalTimeZone;
    }

    @Getter
    @Setter
    public static class AuthorizationServer {
        private int listeningPort;

        @NotNull
        private String authorizationRedirectUrlBase;
    }
}
