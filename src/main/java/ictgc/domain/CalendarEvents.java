package ictgc.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;

/**
 * Read-only iterable of events in a calendar.
 */
@EqualsAndHashCode
public class CalendarEvents implements Iterable<CalendarEvent> {

    @Delegate(types = Iterable.class)
    private final List<CalendarEvent> events;

    public CalendarEvents(List<CalendarEvent> events) {
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public int size() {
        return events.size();
    }
}
