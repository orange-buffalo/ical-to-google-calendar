# Synchronizes iCalendar feeds into Google Calendars
Why? Although Google supports iCal, it synchronizes external calendars extremely rarely, up to once per day. 
This is too long for current ever-changing world. 

# How does this tool work?
Automatically fetches iCalendar feeds and propagates them into Google Calendars:
* supports any synchronization interval;
* supports any number of feeds to be synchronized into any number of Google Calendars;
* supports multiple Google Accounts.

Limitations:
* it was decided to not implement real merging as a too complex operation; thus whenever iCal data is changed, 
all the previously created tasks are removed and new ones created based on the feed content;
* it is up to your Google Developer Account how many requests you can do and thus how often 
and how many calendars can be synchronized;
* it is your responsibility to host and run the tool.

# Setup Guide

## Recommended Setup

The guide assumes your are running the application in a Docker container, and reverse proxy is used for TLS termination.
If this is not the case, corresponding adjustments are required.

## Prepare your hosting

1. Define an `authorization-redirect-url-base` - a URL where the tool will be listening to Google's auth callbacks, 
before TLS termination (defaults `http://localhost:<port>`).
2. Define a `port` to be opened in the internal network to listen to authorization callback (default `9889`).

## Prepare Google Calendar API account

1. Make sure you have access to Google Developer Console.
2. Follow [this guide](https://console.developers.google.com/flows/enableapi?apiid=calendar) to create a project and enable Calendar API.
3. Setup `Authorized redirect URIs` for created credentials: set it to `<authorization-redirect-url-base>/google-calendar-auth-callback`.
4. Download `client_secret.json`.

## Prepare files

1. Application will look for a Spring Boot config file in the `/data/config` directory. When running the image, 
either provide a volume with the config file and Google credentials, or mount a host folder containing the same.
2. Below is the default config, extend it with the user and flow configuration, 
and re-write the files location if desired.
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
        listening-port: "9889"
        authorization-redirect-url-base: "http://localhost:9889"
      synchronization-schedule-delay: "300000"
      authorization-storage-directory: "/data/storage"
      google-client-secrets-file: "/data/config/client-secret.json"
    ```

## Start the tool

`docker run -d -p 9889:9889 --mount type=bind,source=<host-data-folder>,target=/data orangebuffalo/ical-to-google-calendar`

## Authorize the API

You will see in logs the URLs for every user to authorize application to access their Google Calendars. 
Follow the links and grant accesses. In future emails will be sent with the links for authorization.

