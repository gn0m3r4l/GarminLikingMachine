package de.gn0m3r4l.garminliker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Starter Klasse der Anwendung.
 */
public class Main {

    /**
     * Anwendungsstarter.
     *
     * @param args Erwartet wird eine Liste von Argumenten in der
     *             Form "provider=url".
     *             Beispiel: "strava=https://www.strava.com/athletes/12345"
     *             oder "garmin=https://connect.garmin.com/app/profile/12345". Es können mehrere URLs für den
     *             gleichen Anbieter angegeben werden, z.B. "strava=https://www.strava.com/athletes/12345
     *             strava=https://www.strava.com/athletes/67890".
     */
    public static void main(String[] args) {
        final Map<String, List<String>> providerMap = splitArgs(args);

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "localhost:9222");
        WebDriver driver = new ChromeDriver(options);

        StravaChecker.getInstance().run(driver, providerMap.get("strava"));
        GarminChecker.getInstance().run(driver, providerMap.get("garmin"));
        //2nd run for strava as it feels that it shows different stuff each time
        StravaChecker.getInstance().run(driver, providerMap.get("strava"));

        driver.quit();
        System.exit(0);
    }

    /**
     * Parst die Kommandozeilenargumente und gruppiert sie nach Anbieter. Erwartet wird eine Liste von Argumenten in
     * der Form "provider=url". Beispiel: "strava=https://www.strava.com/athletes/12345" oder
     * "garmin=https://connect.garmin.com/app/profile/12345". Es können mehrere URLs für den gleichen Anbieter
     * angegeben werden, z.B. "strava=https://www.strava.com/athletes/12345
     * strava=https://www.strava.com/athletes/67890". Gibt eine Map zurück, in der die Schlüssel die
     * Anbieternamen (z.B. "strava", "garmin") und die Werte Listen von URLs sind.
     *
     * @param args Unveränderte Kommandozeilenargumente, die in der Form "provider=url" erwartet werden.
     * @return ProviderMap: Eine Map, in der die Schlüssel die Anbieternamen (z.B. "strava", "garmin")
     * und die Werte Listen von URLs sind.
     */
    private static Map<String, List<String>> splitArgs(final String[] args) {
        Map<String, List<String>> providerMap = new HashMap<>();
        for (var arg : args) {
            var split = arg.split("=", 2);
            if (split.length == 2) {
                var provider = split[0].trim().toLowerCase();
                var url = split[1].trim();
                providerMap.computeIfAbsent(provider, k -> new java.util.ArrayList<>()).add(url);
            } else {
                System.out.println("Invalid argument format: " + arg + ". Expected format: provider=url");
            }
        }
        return providerMap;
    }
}
