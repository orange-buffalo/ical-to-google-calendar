package ictgc;

import ictgc.google.GoogleApiService;
import ictgc.ical.CalendarReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CalendarsSynchronizer {

    private final TaskExecutor taskExecutor;
    private final ApplicationProperties config;
    private final Collection<UserFlow> userFlows;
    private final GoogleApiService googleApiService;
    private final CalendarReader calendarReader;

    @Autowired
    public CalendarsSynchronizer(
            TaskExecutor taskExecutor, ApplicationProperties config, GoogleApiService googleApiService,
            CalendarReader calendarReader) {

        this.taskExecutor = taskExecutor;
        this.config = config;
        this.googleApiService = googleApiService;
        this.calendarReader = calendarReader;
        this.userFlows = getUserFlows();
    }

    @Scheduled(fixedDelayString = "${ical-to-google-calendar.synchronization-schedule-delay}")
    public void synchronizeCalendars() {
        log.info("launching synchronization");

        // don't want to bother with aspectj proxies
        userFlows.forEach(userFlow -> taskExecutor.execute(userFlow::synchronizeUserCalendars));
    }

    private Collection<UserFlow> getUserFlows() {
        Map<String, UserFlow> userFlowsMap = new HashMap<>();
        for (ApplicationProperties.Flow configFlow : config.getFlows()) {
            UserFlow userFlow = userFlowsMap.computeIfAbsent(configFlow.getUserId(), this::createUserFlowByUserId);
            userFlow.getCalendarFlows().add(
                    new CalendarFlow(configFlow.getICalUrl(), configFlow.getGoogleCalendarName()));
        }
        return userFlowsMap.values();
    }

    private UserFlow createUserFlowByUserId(String userId) {
        ApplicationProperties.User configUser = config.getUsers().stream()
                .filter(user -> user.getId().equals(userId))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("User " + userId + " is not found"));

        return new UserFlow(configUser.getId(), configUser.getEmail(), calendarReader, googleApiService);
    }

}
