package ictgc;

import java.time.ZoneId;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import ictgc.domain.CalendarEvents;
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

    /**
     * Default time zone to be used if iCal does not provide one.
     */
    private final ZoneId defaultTimeZone;

    public CalendarFlow(@Nonnull String iCalUrl,
                        @Nonnull String googleCalendarName,
                        @Nonnull ZoneId defaultTimeZone) {

        this.iCalUrl = iCalUrl;
        this.googleCalendarName = googleCalendarName;
        this.defaultTimeZone = defaultTimeZone;
    }
}
