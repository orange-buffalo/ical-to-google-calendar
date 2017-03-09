# Synchronizes iCalendar feeds into Google Calendars
Why? Although Google supports iCal, it synchronizes external calendars extremely rarely, up to once per day. This is too long for current ever-changing world. 

# How does this tool work?
Automatically fetches iCalendar feeds and propagates them into Google Calendars:
* supports any synchronization interval;
* supports any number of feeds to be synchronized into any number of Google Calendars;
* supports multiple Google Accounts.

Limitations:
* it was decided to not implement real merging as too complex operation; thus whenever iCal data is changed, all the previously created tasks are removed and new ones created based on feed content;
* it is up to your Google Developer Account how many requests you can do and thus how often and how many calendars can be synchronized;
* it is your responsibility to host and run the tool.

# Setup Guide

## Prepare your hosting

1. Define a `host` where tool will be running (default `localhost`).
2. Define a `port` to be opened to listen to authorization callback (default `9889`).

## Prepare Google Calendar API account

1. Make sure you have access to Google Developer Console.
2. Follow [this guide](https://console.developers.google.com/flows/enableapi?apiid=calendar) to create a project and enable Calendar API.
3. Setup `Authorized redirect URIs` for created credentials: set it to `http://<host>:<port>/google-calendar-auth-callback`.
4. Download `client_secret.json`.

## Prepare binaries

1. Clone the repo.
2. Run `./gradlew bootRepackage`.
3. Copy `./build/libs/ical-to-google-calendar.jar` to desired directory.

## Prepare files

1. Define the directory where user credentials will be saved by Google API (default `~/.ical-to-google-calendar/storage`)
2. Define where you want to store your `client_secret.json` and copy it there (default `~/.ical-to-google-calendar/client_secret.json`).
3. Create config file (for instance, `application.yml`), overriding any default value you have customized and defining users and calendars to synchronize:
```yaml
ical-to-google-calendar:
#  users:
#    - id: "user-id"
#      email: "user@email"
#  flows:
#    - user-id: "user-id"
#      i-cal-url: "url-to-feed"
#      google-calendar-name: "google-calendar-name"
  authorization-server:
    port: "9889"
    host: "localhost"
  synchronization-schedule-delay: "300000"
  google-client-secrets-file: "~/.ical-to-google-calendar/client_secret.json"
  authorization-storage-directory: "~/.ical-to-google-calendar/storage"
```

## Start the tool

`java -jar ical-to-google-calendar.jar -Dspring.config.location=<path_to_application.yml>`

## Authorize the API

You will see in logs the URLs for every user to authorize application to access their Google Calendars. Follow the links and grant accesses. In future emails will be sent with the links for authorization.

