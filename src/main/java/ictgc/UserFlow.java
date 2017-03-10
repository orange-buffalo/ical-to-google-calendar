package ictgc;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import ictgc.domain.CalendarData;
import ictgc.domain.CalendarEvent;
import ictgc.google.GoogleApiService;
import ictgc.google.JsonBatchErrorCallback;
import ictgc.ical.CalendarReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.ParserException;
import org.apache.commons.io.IOUtils;

@Getter
@Slf4j
class UserFlow {

    private static final String UUID_PROPERTY_NAME = "ical-to-google-calendar-uuid";

    private String userId;
    private String userEmail;
    private List<CalendarFlow> calendarFlows = new ArrayList<>();
    private boolean active;
    private final CalendarReader calendarReader;
    private final GoogleApiService googleApiService;

    public UserFlow(String userId, String userEmail, CalendarReader calendarReader, GoogleApiService googleApiService) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.calendarReader = calendarReader;
        this.googleApiService = googleApiService;
    }

    public void start() {
        this.active = true;
    }

    public void finish() {
        this.active = false;
    }

    public void synchronizeUserCalendars() {
        if (active) {
            log.info("{} is still active, skipping", userId);
            return;
        }

        try {
            start();

            log.info("starting synchronizing data for {}", userId);

            Calendar googleCalendarService = googleApiService.getCalendarService(userId, userEmail);
            for (CalendarFlow calendarFlow : calendarFlows) {
                try {
                    CalendarData calendarData = readICalendar(calendarFlow);

                    if (calendarData != null) {
                        mergeCalendarDataToGoogleCalendar(
                                googleCalendarService, calendarData, calendarFlow.getGoogleCalendarName());

                        calendarFlow.setPreviousData(calendarData);
                    }
                }
                catch (GoogleJsonResponseException jsonException) {
                    log.error("exception while processing calendar flow " + calendarFlow, jsonException);

                    GoogleJsonError jsonError = jsonException.getDetails();
                    if (requiresCredentialsReset(jsonError)) {
                        resetCredentials();
                    }
                }
                catch (TokenResponseException tokenError) {
                    log.error("exception while processing calendar flow " + calendarFlow, tokenError);
                    resetCredentials();
                }
                catch (Exception e) {
                    calendarFlow.setPreviousData(null);

                    log.error("exception while processing calendar flow " + calendarFlow, e);
                }
            }

            log.info("done, {} is synchronized", userId);
        }
        finally {
            finish();
        }
    }

    private void resetCredentials() {
        googleApiService.resetCredentials(userId);
        log.info("credentials cleared");
    }

    private boolean requiresCredentialsReset(GoogleJsonError jsonError) {
        int errorCode = jsonError.getCode();
        return errorCode == 401;
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
        if (eventsToCreate.isEmpty()) {
            log.info("no events to create, skipping");
            return;
        }

        log.info("creating {} events in {}", eventsToCreate.size(), googleCalendarId);

        JsonBatchCallback<Event> callback = new JsonBatchErrorCallback<>();
        BatchRequest googleBatchRequest = googleCalendarService.batch();
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

            eventsService.insert(googleCalendarId, googleCalendarEvent)
                    .queue(googleBatchRequest, callback);
        }

        googleBatchRequest.execute();

        log.info("inserted");
    }

    private void deleteExistingEvents(Calendar googleCalendarService, String googleCalendarId) throws IOException {
        log.info("deleting events from {}", googleCalendarId);

        Calendar.Events eventsService = googleCalendarService.events();
        List<Event> events = eventsService.list(googleCalendarId)
                .setMaxResults(2500)
                .setShowDeleted(Boolean.FALSE)
                .execute()
                .getItems();

        if (events.isEmpty()) {
            log.info("no events in this calendar, skipping deletion");
            return;
        }

        JsonBatchCallback<Void> callback = new JsonBatchErrorCallback<>();
        BatchRequest batchRequest = googleCalendarService.batch();

        for (Event event : events) {
            if (getICalUuid(event) != null) {
                eventsService.delete(googleCalendarId, event.getId())
                        .queue(batchRequest, callback);
            }
        }

        batchRequest.execute();

        log.info("deleted {} events", events.size());
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
}
