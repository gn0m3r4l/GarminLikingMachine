package de.gn0m3r4l.garminliker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;

import de.gn0m3r4l.garminliker.customActions.GarminChecker;
import de.gn0m3r4l.garminliker.customActions.KomootChecker;
import de.gn0m3r4l.garminliker.customActions.StravaChecker;
import de.gn0m3r4l.garminliker.utils.TimeConstants;

/**
 * Starter Klasse der Anwendung.
 */
public class Main {

    public static final int WEBSITE_MAX_RETRY_COUNT = 3;

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
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().connectOverCDP("http://localhost:9222");
            BrowserContext context = browser.contexts().isEmpty() ?
                    browser.newContext() : browser.contexts().getFirst();
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();
            page.setDefaultTimeout(TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR);
            page.setDefaultNavigationTimeout(TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR);
            page.bringToFront();

            List<Consumer<Page>> checks = List.of(
                    currentPage -> StravaChecker.getInstance().run(currentPage, providerMap.get("strava")),
                    currentPage -> KomootChecker.getInstance().run(currentPage, providerMap.get("komoot")),
                    currentPage -> GarminChecker.getInstance().run(currentPage, providerMap.get("garmin")),
                    // 2nd run for strava as it feels that it shows different stuff each time
                    currentPage -> StravaChecker.getInstance().run(currentPage, providerMap.get("strava"))
            );

            for (Consumer<Page> check : checks) {
                for (int retry = 0; retry < WEBSITE_MAX_RETRY_COUNT; retry++) {
                    if (tryCheck(check, page)) {
                        break;
                    }
                }
            }

            browser.close();
        }
    }

    private static boolean tryCheck(Consumer<Page> check, Page page) {
        try {
            check.accept(page);
            return true;
        } catch (TimeoutError te) {
            System.err.println("Timeout while processing provider check: " + te.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error while processing provider check: " + e.getMessage());
            return false;
        }
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
