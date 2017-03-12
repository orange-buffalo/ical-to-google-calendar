package ictgc;

import ictgc.domain.CalendarEvents;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Describes data flow form iCalendar toGoogle Calendar.
 */
@Getter
@ToString(exclude = "previousData")
class CalendarFlow {

    /**
     * URL to iCalendar feed.
     */
    @Nonnull
    private final String iCalUrl;

    /**
     * Name of Google Calendar to sync data to.
     */
    @Nonnull
    private final String googleCalendarName;

    /**
     * Calendar events retrieved in the previous synchronization iteration.
     * May be used to skip synchronization if not changes in data occured.
     */
    @Setter
    @Nullable
    private CalendarEvents previousData;

    public CalendarFlow(@Nonnull String iCalUrl, @Nonnull String googleCalendarName) {
        this.iCalUrl = iCalUrl;
        this.googleCalendarName = googleCalendarName;
    }
}
