package ictgc.domain;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nonnull;
import java.time.ZonedDateTime;

/**
 * An event abstraction used by application.
 * To be produced by iCalendar readers and consumed by Google Calendar writers.
 */
@Getter
@Builder
@EqualsAndHashCode
public class CalendarEvent {

    /**
     * Unique ID of the event as it is present in iCalendar feed.
     */
    @Nonnull
    private String uuid;

    /**
     * Title of this event.
     */
    @Nonnull
    private String summary;

    /**
     * Text body of event description.
     */
    @Nonnull
    private String description;

    /**
     * Start time of this event.
     */
    @Nonnull
    private ZonedDateTime startTime;

    /**
     * End time of this event. May be equal to {@link CalendarEvent#startTime}
     * if {@link CalendarEvent#allDayEvent} is {@code true}.
     */
    @Nonnull
    private ZonedDateTime endTime;

    /**
     * Indicates if this event's {@link CalendarEvent#startTime} and {@link CalendarEvent#endTime}
     * should be treated as specific time or as dates. If this field is {@code true},
     * event is "all day event" and has not particular start/end time.
     * Otherwise {@link CalendarEvent#startTime} and {@link CalendarEvent#endTime} represent
     * time when event is scheduled to start and to end.
     */
    private boolean allDayEvent;

}
