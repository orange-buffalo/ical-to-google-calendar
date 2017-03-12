package ictgc.ical;

import ictgc.domain.CalendarEvent;
import ictgc.domain.CalendarEvents;
import ictgc.domain.CalendarSynchronizationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import org.springframework.stereotype.Service;

/**
 * Reads the iCalendar feed and produces {@link CalendarEvents}.
 */
@Slf4j
@Service
public class CalendarReader {

    /**
     * Reads the feed and generates {@link CalendarEvents}.
     * @param calendarBody feed to parse
     * @return events in provided feed
     */
    public @Nonnull CalendarEvents readCalendar(String calendarBody) {
        try {
            CalendarBuilder calendarBuilder = new CalendarBuilder();
            Calendar iCalendar = calendarBuilder.build(new StringReader(calendarBody));

            ComponentList<VEvent> iCalEvents = iCalendar.getComponents(Component.VEVENT);
            List<CalendarEvent> calendarEvents = new ArrayList<>();
            for (VEvent iCalEvent : iCalEvents) {
                Date startDate = iCalEvent.getStartDate().getDate();

                DtEnd dtEndDate = iCalEvent.getEndDate();
                Date endDate = (dtEndDate == null) ? startDate : dtEndDate.getDate();

                calendarEvents.add(CalendarEvent.builder()
                        .summary(iCalEvent.getSummary().getValue())
                        .description(iCalEvent.getDescription().getValue())
                        .uuid(iCalEvent.getUid().getValue())
                        .startTime(new java.util.Date(startDate.getTime()))
                        .endTime(new java.util.Date(endDate.getTime()))
                        .allDayEvent(!(startDate instanceof DateTime))
                        .build());
            }

            return new CalendarEvents(calendarEvents);
        }
        catch (IOException | ParserException e) {
            throw new CalendarSynchronizationException(e);
        }
    }

}
