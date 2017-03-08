package ictgc.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class CalendarData {

    private List<CalendarEvent> events = new ArrayList<>();

    public void addEvent(CalendarEvent event) {
        events.add(event);
    }

}
