package ictgc.ical;

import ictgc.domain.CalendarData;
import ictgc.domain.CalendarEvent;
import java.io.IOException;
import java.io.StringReader;
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

@Slf4j
@Service
public class CalendarReader {

    public CalendarData parseCalendar(String calendarString) throws IOException, ParserException {
        CalendarBuilder calendarBuilder = new CalendarBuilder();
        Calendar calendar = calendarBuilder.build(new StringReader(calendarString));

        ComponentList<VEvent> events = calendar.getComponents(Component.VEVENT);
        CalendarData calendarData = new CalendarData();
        for (VEvent vEvent : events) {
            Date startDate = vEvent.getStartDate().getDate();

            DtEnd dtEndDate = vEvent.getEndDate();
            Date endDate = (dtEndDate == null) ? startDate : dtEndDate.getDate();

            calendarData.addEvent(CalendarEvent.builder()
                    .summary(vEvent.getSummary().getValue())
                    .description(vEvent.getDescription().getValue())
                    .uuid(vEvent.getUid().getValue())
                    .startTime(new java.util.Date(startDate.getTime()))
                    .endTime(new java.util.Date(endDate.getTime()))
                    .allDayEvent(!(startDate instanceof DateTime))
                    .build());
        }

        return calendarData;
    }

}
