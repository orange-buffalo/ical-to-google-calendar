package ictgc.domain;

import java.util.Date;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@Builder
@EqualsAndHashCode
public class CalendarEvent {

    private String uuid;

    private String summary;

    private String description;

    private Date startTime;

    private Date endTime;

    private boolean allDayEvent;

}
