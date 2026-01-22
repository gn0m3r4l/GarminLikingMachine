# Garmin Liking Machine
## About
This tool uses an existing chrome session to like activities from the current user. By default (2026/01/12), 10 activities are shown initially. Those are all grabbed. To get more, scroll down and click "Mehr anzeigen".

Already liked activities are called, but like state won't be touched.

After finishing the process, the script will close the browser to free resources (chrome-driver locks code folder).

## How to use
1. Adjust the bat file to match your chrome path. If you change port, you have to do the same change in code. It is suggested to use a new user-dir to avoid damage to existing profiles
2. Adjust the profile list in static block of the code. If list is empty or null, the current shown page will be used.
2. Run the bat file. (Make sure that there is only one chrome instance running with the specified user-dir and debugging port).
3. Login to Garmin Connect
4. Open profile of a person you want to like activities from.
5. Run the app. (Again: If the profile list is empty or null, the current profile is used with all shown activities (depending on how often one pressed the button, see above))
6. Optional: Repeat from step 2 if you want to like more activities from other persons.