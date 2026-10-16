# Garmin/Strava Liking Machine
## About
This tool uses an existing Chrome debug session and connects to it via Playwright to like activities from a logged in user. 

Already liked activities are called, but like state won't be touched.

After finishing the process, the script disconnects from the browser session after the run.

### For bigger lists of URLs

If there is a bigger list of URLs given for the same provider, it will be splitted and handled with a delay. This shall avoid cloudflare to strike.

## How to use
1. Adjust the bat file to match your chrome path and used providers. If you change port, you have to do the same change in code. It is suggested to use a new user-dir to avoid damage to existing profiles
2. Run the bat file. (Make sure that there is only one chrome instance running with the specified user-dir and debugging port).
3. Login to Garmin and/or Strava
5. Run the app with Parameters: garmin=, garmin=, komoot=, strava=... Only keys "garmin", "strava" and "komoot" are supported, but you can use them multiple times. 
Garmin URLs must point either to the newsfeed (`https://connect.garmin.com/app/newsfeed`) or to profiles, Strava URLs must point to dashboards with listed activities.

### Garmin newsfeed mode
With `garmin=https://connect.garmin.com/app/newsfeed` the app opens the newsfeed, checks the people filter after Garmin has loaded the stored preference and, if it is not exactly "Freunde", switches it and reloads the feed and likes every not yet liked activity from top to bottom. The feed is a virtualized list, so cards are tracked by activity id while scrolling. At the end of the rendered list it scrolls and waits for the lazy loading (spinner gone, new entries rendered, max. 20 s).
It stops when `LIKE_CHAIN_LIMIT` (5) already liked activities in a row were found, nothing more is loaded after 3 attempts (end of feed) or `MAX_LIKES_PER_RUN` (150) is reached. Profile URLs still work and can be mixed with the newsfeed URL.
```powershell
java -jar target/GarminLikingMachine-1.0-SNAPSHOT.jar garmin=https://connect.garmin.com/modern/profile/12345678 garmin=https://connect.garmin.com/modern/profile/87654321 strava=https://www.strava.com/dashboard
```

## Code quality / Complexity report
This repository uses a local, checked-in Checkstyle ruleset to report cyclomatic complexity (goal: methods between 7–9 where practical).

To generate the Checkstyle complexity report locally (report-only, does not fail the build):

```powershell
cd C:\Users\daniel.plagge\Code\GarminLikingMachine
mvn checkstyle:checkstyle -DskipTests
```

After running the command, the Checkstyle report is generated under `target/site` (and output is printed to the console). The Checkstyle ruleset is located in `config/checkstyle/checkstyle.xml` (CyclomaticComplexity max=9).

The checkstyle configuration is based on a reduced version of the Google Java Style Guide, with some adjustments to fit the project's needs. The main focus is on maintaining a reasonable cyclomatic complexity to ensure code readability and maintainability.
