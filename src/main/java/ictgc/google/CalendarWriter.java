package ictgc.google;

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
import ictgc.domain.CalendarEvent;
import ictgc.domain.CalendarEvents;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Writes {@link CalendarEvents} to Google Calendar.
 * Currently removes all previously created events and re-creates them.
 * In future, more sophisticated merge strategies may be implemented.
 */
@Service
@Slf4j
public class CalendarWriter {

    private static final String UUID_PROPERTY_NAME = "ical-to-google-calendar-uuid";
    private final GoogleApiService googleApiService;

    @Autowired
    public CalendarWriter(@Nonnull GoogleApiService googleApiService) {
        this.googleApiService = googleApiService;
    }

    /**
     * Merges calendar events to Google Calendar.
     *
     * @param userId             ID os user who owns the calendar.
     * @param userEmail          email os user who owns the calendar.
     * @param calendarEvents     events to be synchronized to the calendar.
     * @param googleCalendarName name of Google calendar where events should be synchronized to.
     * @throws IOException in case of synchronization issues.
     */
    public void mergeCalendarDataToGoogleCalendar(
            @Nonnull String userId, @Nonnull String userEmail,
            @Nonnull CalendarEvents calendarEvents, @Nonnull String googleCalendarName)
            throws IOException {

        try {
            log.info("starting merging calendar data into {}", googleCalendarName);

            Calendar googleCalendarService = googleApiService.getCalendarService(userId, userEmail);

            CalendarListEntry googleCalendar = getGoogleCalendar(googleCalendarService, googleCalendarName);
            String googleCalendarId = googleCalendar.getId();

            deleteExistingEvents(googleCalendarService, googleCalendarId);
            createEvents(googleCalendarService, calendarEvents, googleCalendarId);

            log.info("all done");
        }
        catch (GoogleJsonResponseException jsonException) {
            GoogleJsonError jsonError = jsonException.getDetails();
            if (requiresCredentialsReset(jsonError)) {
                resetCredentials(userId);
            }
            throw jsonException;
        }
        catch (TokenResponseException tokenException) {
            resetCredentials(userId);
            throw tokenException;
        }
    }

    private void resetCredentials(String userId) {
        googleApiService.resetCredentials(userId);
        log.info("credentials cleared");
    }

    private boolean requiresCredentialsReset(GoogleJsonError jsonError) {
        int errorCode = jsonError.getCode();
        return errorCode == 401;
    }

    private void createEvents(Calendar googleCalendarService, CalendarEvents calendarEvents, String googleCalendarId)
            throws IOException {

        if (calendarEvents.isEmpty()) {
            log.info("no events to create, skipping");
            return;
        }

        log.info("creating {} events in {}", calendarEvents.size(), googleCalendarId);

        JsonBatchCallback<Event> callback = new JsonBatchErrorCallback<>();
        BatchRequest googleBatchRequest = googleCalendarService.batch();
        Calendar.Events eventsService = googleCalendarService.events();

        for (CalendarEvent calendarEvent : calendarEvents) {
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
