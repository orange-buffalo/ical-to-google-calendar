package ictgc;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nonnull;

import ictgc.domain.CalendarEvents;
import ictgc.google.CalendarWriter;
import ictgc.ical.CalendarReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

/**
 * Synchronizes all the calendars for one user.
 */
@Slf4j
class UserFlow {

    private String userId;
    private String userEmail;
    private List<CalendarFlow> calendarFlows = new ArrayList<>();
    private final CalendarReader calendarReader;
    private final CalendarWriter calendarWriter;
    private final ReentrantLock lock = new ReentrantLock();

    public UserFlow(@Nonnull String userId,
                    @Nonnull String userEmail,
                    @Nonnull CalendarReader calendarReader,
                    @Nonnull CalendarWriter calendarWriter) {

        this.userId = userId;
        this.userEmail = userEmail;
        this.calendarReader = calendarReader;
        this.calendarWriter = calendarWriter;
    }

    /**
     * Thread-safe synchronization of user calendars.
     */
    public void synchronizeUserCalendars() {
        if (!lock.tryLock()) {
            log.trace("{} synch is still active, skipping", userId);
            return;
        }

        try {
            log.trace("starting synchronizing data for {}", userId);

            for (CalendarFlow calendarFlow : calendarFlows) {
                try {
                    CalendarEvents calendarEvents = readICalendar(calendarFlow);

                    if (calendarEvents != null) {
                        log.info("new data detected for {}, continue synch", calendarFlow);

                        calendarWriter.mergeCalendarDataToGoogleCalendar(
                                userId, userEmail, calendarEvents, calendarFlow.getGoogleCalendarName());

                        calendarFlow.setPreviousData(calendarEvents);

                        log.info("{} is synchronized", calendarFlow);
                    } else {
                        log.trace("no changes in feed, skipping synchronization");
                    }
                } catch (Exception e) {
                    calendarFlow.setPreviousData(null);

                    log.error("exception while processing calendar flow " + calendarFlow, e);
                }
            }

            log.trace("done, {} is processed", userId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Registers new flow for this user.
     */
    public void addCalendarFlow(@Nonnull CalendarFlow calendarFlow) {
        this.calendarFlows.add(calendarFlow);
    }

    private CalendarEvents readICalendar(CalendarFlow calendarFlow) throws IOException {
        String iCalUrl = calendarFlow.getICalUrl();
        log.trace("reading calendar feed: {}", iCalUrl);

        String currentCalendarFeedContent = IOUtils.toString(new URL(iCalUrl), StandardCharsets.UTF_8);

        log.trace("feed retrieved");

        CalendarEvents currentData = calendarReader.readCalendar(currentCalendarFeedContent, calendarFlow.getDefaultTimeZone());

        log.trace("feed parsed");

        CalendarEvents previousData = calendarFlow.getPreviousData();
        if (previousData != null && previousData.equals(currentData)) {
            return null;
        }

        return currentData;
    }

}
