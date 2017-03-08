package ictgc;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import ictgc.domain.CalendarData;
import ictgc.domain.CalendarEvent;
import ictgc.google.GoogleApiService;
import ictgc.ical.CalendarReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.ParserException;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CalendarsSynchronizer {

    private static final String UUID_PROPERTY_NAME = "ical-to-google-calendar-uuid";

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
        userFlows.forEach(userFlow -> taskExecutor.execute(() -> synchronizeUserCalendars(userFlow)));
    }

    private void synchronizeUserCalendars(UserFlow userFlow) {
        String userId = userFlow.getUserId();

        if (userFlow.isActive()) {
            log.info("{} is still active, skipping", userId);
            return;
        }

        try {
            userFlow.start();

            log.info("starting synchronizing data for {}", userId);

            Calendar googleCalendarService = googleApiService.getCalendarService(userId, userFlow.getUserEmail());
            for (CalendarFlow calendarFlow : userFlow.getCalendarFlows()) {
                CalendarData calendarData = readICalendar(calendarFlow);

                if (calendarData != null) {
                    mergeCalendarDataToGoogleCalendar(
                            googleCalendarService, calendarData, calendarFlow.getGoogleCalendarName());
                }
            }

            log.info("done, {} is synchronized", userId);
        }
        catch (IOException | ParserException e) {
            throw new IllegalStateException(e);
        }
        finally {
            userFlow.finish();
        }
    }

    private CalendarData readICalendar(CalendarFlow calendarFlow) throws IOException, ParserException {
        String iCalUrl = calendarFlow.getICalUrl();
        log.info("reading calendar feed: {}", iCalUrl);

        String currentCalendarFeedContent = IOUtils.toString(new URL(iCalUrl), StandardCharsets.UTF_8);

        log.info("feed retrieved");

        CalendarData currentData = calendarReader.parseCalendar(currentCalendarFeedContent);

        log.info("feed parsed");

        CalendarData previousData = calendarFlow.getPreviousData();
        if (previousData != null && previousData.equals(currentData)) {
            log.info("no changes in feed, skipping synchronization");
            return null;
        }
        calendarFlow.setPreviousData(currentData);

        log.info("new data detected, continue synch");

        return currentData;
    }

    private void mergeCalendarDataToGoogleCalendar(
            Calendar googleCalendarService, CalendarData calendarData, String googleCalendarName) throws IOException {

        log.info("starting merging calendar data into {}", googleCalendarName);

        CalendarListEntry googleCalendar = getGoogleCalendar(googleCalendarService, googleCalendarName);
        String googleCalendarId = googleCalendar.getId();

        deleteExistingEvents(googleCalendarService, googleCalendarId);
        createEvents(googleCalendarService, calendarData, googleCalendarId);

        log.info("all done");
    }

    private void createEvents(Calendar googleCalendarService, CalendarData calendarData, String googleCalendarId)
            throws IOException {

        List<CalendarEvent> eventsToCreate = calendarData.getEvents();
        log.info("creating {} events in {}", eventsToCreate.size(), googleCalendarId);

        Calendar.Events eventsService = googleCalendarService.events();
        for (CalendarEvent calendarEvent : eventsToCreate) {
            Event.ExtendedProperties extendedProperties = new Event.ExtendedProperties();
            HashMap<String, String> privateProperties = new HashMap<>();
            extendedProperties.setPrivate(privateProperties);
            privateProperties.put(UUID_PROPERTY_NAME, calendarEvent.getUuid());

            Event googleCalendarEvent = new Event()
                    .setSummary(calendarEvent.getSummary())
                    .setDescription(calendarEvent.getDescription())
                    .setExtendedProperties(extendedProperties);

            if (calendarEvent.isAllDayEvent()) {
                googleCalendarEvent
                        .setStart(new EventDateTime().setDate(
                                new DateTime(true, calendarEvent.getStartTime().getTime(), null)))
                        .setEnd(new EventDateTime().setDate(
                                new DateTime(true, calendarEvent.getEndTime().getTime(), null)));
            }
            else {
                googleCalendarEvent
                        .setStart(new EventDateTime().setDateTime(
                                new DateTime(false, calendarEvent.getStartTime().getTime(), null)))
                        .setEnd(new EventDateTime().setDateTime(
                                new DateTime(false, calendarEvent.getEndTime().getTime(), null)));
            }

            eventsService.insert(googleCalendarId, googleCalendarEvent).execute();
        }

        log.info("done");
    }

    private void deleteExistingEvents(Calendar googleCalendarService, String googleCalendarId) throws IOException {
        log.info("deleting events from {}", googleCalendarId);

        Calendar.Events eventsService = googleCalendarService.events();
        Events events = eventsService.list(googleCalendarId).execute();
        for (Event event : events.getItems()) {
            if (getICalUuid(event) != null) {
                eventsService.delete(googleCalendarId, event.getId()).execute();
            }
        }

        log.info("deleted");
    }

    private String getICalUuid(Event event) {
        Event.ExtendedProperties extendedProperties = event.getExtendedProperties();
        if (extendedProperties != null) {
            Map<String, String> privateProperties = extendedProperties.getPrivate();
            if (privateProperties != null) {
                return privateProperties.get(UUID_PROPERTY_NAME);
            }
        }
        return null;
    }

    private CalendarListEntry getGoogleCalendar(Calendar googleCalendarService, String googleCalendarName)
            throws IOException {

        CalendarList calendarList = googleCalendarService.calendarList().list().execute();
        return calendarList.getItems().stream()
                .filter(entry -> entry.getSummary().equals(googleCalendarName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Calendar " + googleCalendarName + " is not found"));
    }

    private Collection<UserFlow> getUserFlows() {
        Map<String, UserFlow> userFlowsMap = new HashMap<>();
        for (ApplicationProperties.Flow configFlow : config.getFlows()) {
            UserFlow userFlow = userFlowsMap.computeIfAbsent(configFlow.getUserId(), this::createUserFlowByUserId);
            userFlow.calendarFlows.add(new CalendarFlow(configFlow.getICalUrl(), configFlow.getGoogleCalendarName()));
        }
        return userFlowsMap.values();
    }

    private UserFlow createUserFlowByUserId(String userId) {
        ApplicationProperties.User configUser = config.getUsers().stream()
                .filter(user -> user.getId().equals(userId))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("User " + userId + " is not found"));

        return new UserFlow(configUser.getId(), configUser.getEmail());
    }

    @Getter
    private static class UserFlow {
        private String userId;
        private String userEmail;
        private List<CalendarFlow> calendarFlows = new ArrayList<>();
        private boolean active;

        private UserFlow(String userId, String userEmail) {
            this.userId = userId;
            this.userEmail = userEmail;
        }

        public void start() {
            this.active = true;
        }

        public void finish() {
            this.active = false;
        }
    }

    @Getter
    private static class CalendarFlow {

        private final String iCalUrl;
        private final String googleCalendarName;

        @Setter
        private CalendarData previousData;

        public CalendarFlow(String iCalUrl, String googleCalendarName) {
            this.iCalUrl = iCalUrl;
            this.googleCalendarName = googleCalendarName;
        }
    }

}
