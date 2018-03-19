package ictgc.ical;

import java.io.IOException;
import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import ictgc.domain.CalendarEvent;
import ictgc.domain.CalendarEvents;
import ictgc.domain.CalendarSynchronizationException;
import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import org.springframework.stereotype.Service;

/**
 * Reads the iCalendar feed and produces {@link CalendarEvents}.
 */
@Slf4j
@Service
public class CalendarReader {

    /**
     * Reads the feed and generates {@link CalendarEvents}.
     *
     * @param calendarBody feed to parse
     * @return events in provided feed
     */
    @Nonnull
    public CalendarEvents readCalendar(String calendarBody, ZoneId defaultTimeZone) {
        try {
            CalendarBuilder calendarBuilder = new CalendarBuilder();
            Calendar iCalendar = calendarBuilder.build(new StringReader(calendarBody));

            ComponentList<VEvent> iCalEvents = iCalendar.getComponents(Component.VEVENT);
            List<CalendarEvent> calendarEvents = new ArrayList<>();
            for (VEvent iCalEvent : iCalEvents) {
                DtStart dtStartDate = iCalEvent.getStartDate();
                DtEnd dtEndDate = iCalEvent.getEndDate();

                calendarEvents.add(CalendarEvent.builder()
                        .summary(iCalEvent.getSummary().getValue())
                        .description(iCalEvent.getDescription().getValue())
                        .uuid(iCalEvent.getUid().getValue())
                        .startTime(datePropertyToZonedDateTime(dtStartDate, defaultTimeZone))
                        .endTime(datePropertyToZonedDateTime((dtEndDate == null) ? dtStartDate : dtEndDate, defaultTimeZone))
                        .allDayEvent(!(dtStartDate.getDate() instanceof DateTime))
                        .build());
            }

            return new CalendarEvents(calendarEvents);
        } catch (IOException | ParserException e) {
            throw new CalendarSynchronizationException(e);
        }
    }

    private ZonedDateTime datePropertyToZonedDateTime(
            DateProperty dateProperty,
            ZoneId defaultTimeZone) {

        TimeZone timeZone = dateProperty.getTimeZone();
        return ZonedDateTime.ofInstant(
                dateProperty.getDate().toInstant(),
                (timeZone == null) ? defaultTimeZone : timeZone.toZoneId());
    }

}
