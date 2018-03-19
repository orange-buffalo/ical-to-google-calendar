package ictgc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("ical-to-google-calendar")
@Component
@Getter
@Setter
public class ApplicationProperties {

    private List<User> users = new ArrayList<>();

    private List<Flow> flows = new ArrayList<>();

    private AuthorizationServer authorizationServer;

    private File authorizationStorageDirectory;

    private File googleClientSecretsFile;

    @Getter
    @Setter
    public static class User {
        private String id;
        private String email;
    }

    @Getter
    @Setter
    public static class Flow {
        private String userId;
        private String iCalUrl;
        private String googleCalendarName;
        private String defaultICalTimeZone;
    }

    @Getter
    @Setter
    public static class AuthorizationServer {
        private int port;
        private String host;
    }
}
