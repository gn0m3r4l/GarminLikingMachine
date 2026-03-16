# Garmin Liking Machine
## About
This tool uses an existing chrome session to like activities from the current user. By default (2026/01/12), 10 activities are shown initially. Those are all grabbed. To get more, scroll down and click "Mehr anzeigen".

Already liked activities are called, but like state won't be touched.

After finishing the process, the script will close the browser to free resources (chrome-driver locks code folder).

## How to use
1. Adjust the bat file to match your chrome path. If you change port, you have to do the same change in code. It is suggested to use a new user-dir to avoid damage to existing profiles
2. Run the bat file. (Make sure that there is only one chrome instance running with the specified user-dir and debugging port).
3. Login to Garmin and/or Strava
5. Run the app with Parameters: garmin=, garmin=, strava=... Only key "garmin" and "strava" are supported, but you can use them multiple times. 
Garmin URLs must point to profiles, Strava URLs must point to dashboards with listed activities.
```powershell
java -jar target/GarminLikingMachine-1.0-SNAPSHOT.jar garmin=https://connect.garmin.com/modern/profile/12345678 garmin=https://connect.garmin.com/modern/profile/87654321 strava=https://www.strava.com/dashboard
```

## Code quality / Complexity report
This repository uses a local, checked-in Checkstyle ruleset to report cyclomatic complexity (goal: methods between 7–9 where practical). PMD has been removed from the default profile because it produced errors with Java 21 in this environment.

To generate the Checkstyle complexity report locally (report-only, does not fail the build):

```powershell
cd C:\Users\daniel.plagge\Code\GarminLikingMachine
mvn checkstyle:checkstyle -DskipTests
```

After running the command, the Checkstyle report is generated under `target/site` (and output is printed to the console). The Checkstyle ruleset is located in `config/checkstyle/checkstyle.xml` (CyclomaticComplexity max=9).

The checkstyle configuration is based on a reduced version of the Google Java Style Guide, with some adjustments to fit the project's needs. The main focus is on maintaining a reasonable cyclomatic complexity to ensure code readability and maintainability.
