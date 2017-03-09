package ictgc;

import ictgc.domain.CalendarData;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString(exclude = "previousData")
class CalendarFlow {

    private final String iCalUrl;
    private final String googleCalendarName;

    @Setter
    private CalendarData previousData;

    public CalendarFlow(String iCalUrl, String googleCalendarName) {
        this.iCalUrl = iCalUrl;
        this.googleCalendarName = googleCalendarName;
    }
}
