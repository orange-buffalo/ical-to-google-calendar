package ictgc;

import ictgc.domain.CalendarSynchronizationException;
import ictgc.google.CalendarWriter;
import ictgc.ical.CalendarReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Schedules synchronization of all users' calendars.
 */
@Service
@Slf4j
public class CalendarsSynchronizer {

    private final TaskExecutor taskExecutor;
    private final Collection<UserFlow> userFlows;

    @Autowired
    public CalendarsSynchronizer(
            @Qualifier("userFlowExecutor") TaskExecutor taskExecutor,
            ApplicationProperties config, CalendarReader calendarReader, CalendarWriter calendarWriter) {

        this.taskExecutor = taskExecutor;
        this.userFlows = getUserFlows(config, calendarReader, calendarWriter);
    }

    /**
     * Schedules synchronization of all users' calendars.
     * Launches synchronization task for every user in a separate thread.
     */
    @Scheduled(fixedDelayString = "${ical-to-google-calendar.synchronization-schedule-delay}")
    public void synchronizeCalendars() {
        log.trace("launching synchronization");

        // don't want to bother with aspectj proxies
        userFlows.forEach(userFlow -> taskExecutor.execute(userFlow::synchronizeUserCalendars));

        log.trace("all flows have been launched");
    }

    private Collection<UserFlow> getUserFlows(
            ApplicationProperties config, CalendarReader calendarReader, CalendarWriter calendarWriter) {

        Map<String, UserFlow> userFlowsMap = new HashMap<>();
        for (ApplicationProperties.Flow configFlow : config.getFlows()) {
            UserFlow userFlow = userFlowsMap.computeIfAbsent(
                    configFlow.getUserId(),
                    userId -> createUserFlowByUserId(userId, config, calendarReader, calendarWriter));
            userFlow.addCalendarFlow(
                    new CalendarFlow(configFlow.getICalUrl(), configFlow.getGoogleCalendarName()));
        }
        return userFlowsMap.values();
    }

    private UserFlow createUserFlowByUserId(
            String userId, ApplicationProperties config, CalendarReader calendarReader, CalendarWriter calendarWriter) {

        ApplicationProperties.User configUser = config.getUsers().stream()
                .filter(user -> user.getId().equals(userId))
                .findAny()
                .orElseThrow(() -> new CalendarSynchronizationException("User " + userId + " is not found"));

        return new UserFlow(configUser.getId(), configUser.getEmail(), calendarReader, calendarWriter);
    }

}
