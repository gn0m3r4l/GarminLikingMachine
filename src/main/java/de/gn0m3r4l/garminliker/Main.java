package de.gn0m3r4l.garminliker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;

import de.gn0m3r4l.garminliker.actions.FitnessCheckActions;
import de.gn0m3r4l.garminliker.customActions.GarminChecker;
import de.gn0m3r4l.garminliker.customActions.GarminNewsfeedChecker;
import de.gn0m3r4l.garminliker.customActions.KomootChecker;
import de.gn0m3r4l.garminliker.customActions.StravaChecker;
import de.gn0m3r4l.garminliker.utils.TimeConstants;

/**
 * Starter Klasse der Anwendung.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
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
     *             Für den Garmin-Newsfeed (Filter "Freunde"): "garmin=https://connect.garmin.com/app/newsfeed".
     */
    public static void main(String[] args) {
        final Map<String, List<String>> providerMap = splitArgs(args);
        // Garmin: Newsfeed-URL -> GarminNewsfeedChecker, alle anderen (Profile) -> GarminChecker
        final List<String> garminUrls = providerMap.getOrDefault("garmin", List.of());
        final List<String> garminFeedUrls = garminUrls.stream()
                .filter(GarminNewsfeedChecker::isNewsfeedUrl).toList();
        final List<String> garminProfileUrls = garminUrls.stream()
                .filter(url -> !GarminNewsfeedChecker.isNewsfeedUrl(url)).toList();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().connectOverCDP("http://localhost:9222");
            BrowserContext context = browser.contexts().isEmpty() ?
                    browser.newContext() : browser.contexts().getFirst();
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();
            page.setDefaultTimeout(TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR);
            page.setDefaultNavigationTimeout(TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR);
            page.bringToFront();

            List<Check> checks = List.of(
                    new Check(StravaChecker.getInstance(), providerMap.getOrDefault("strava", List.of())),
                    new Check(KomootChecker.getInstance(), providerMap.getOrDefault("komoot", List.of())),
                    new Check(GarminNewsfeedChecker.getInstance(), garminFeedUrls),
                    new Check(GarminChecker.getInstance(), garminProfileUrls)
            );

            var aliveLogger = new de.gn0m3r4l.garminliker.utils.AliveLogger();
            Thread aliveLoggerThread = new Thread(aliveLogger);
            // Daemon: haelt die JVM nicht am Leben, falls das Beenden doch einmal haengt
            aliveLoggerThread.setDaemon(true);
            aliveLoggerThread.start();

            List<CheckResult> results = new ArrayList<>();
            for (Check check : checks) {
                results.add(runCheck(check, page));
            }

            aliveLogger.setRunning(false);
            aliveLoggerThread.interrupt();
            browser.close();

            logSummary(results);
        }
    }

    private static CheckResult runCheck(Check check, Page page) {
        var actions = check.actions();
        if (check.urls().isEmpty()) {
            return new CheckResult(actions.getProviderName(), check.urls().size(), 0, "übersprungen", Duration.ZERO);
        }
        long start = System.currentTimeMillis();
        int likes = 0;
        boolean success = false;
        int attempts = 0;
        while (!success && attempts < WEBSITE_MAX_RETRY_COUNT) {
            attempts++;
            success = tryCheck(check, page);
            // likes of failed attempts were given as well, so sum them up
            likes += actions.getGivenLikes();
        }
        String status;
        if (!success) {
            status = "FEHLER nach " + attempts + " Versuchen";
        } else if (actions.likeLimitReached()) {
            status = "Like-Limit erreicht";
        } else {
            status = attempts > 1 ? "OK (" + attempts + " Versuche)" : "OK";
        }
        return new CheckResult(actions.getProviderName(), check.urls().size(), likes, status,
                Duration.ofMillis(System.currentTimeMillis() - start));
    }

    private static void logSummary(List<CheckResult> results) {
        var sb = new StringBuilder("\n===== Zusammenfassung =====\n");
        int totalLikes = 0;
        Duration totalDuration = Duration.ZERO;
        for (CheckResult result : results) {
            sb.append(String.format("%-16s %3d URLs  %4d Likes  %6s  %s%n", result.provider(), result.urlCount(),
                    result.likes(), formatDuration(result.duration()), result.status()));
            totalLikes += result.likes();
            totalDuration = totalDuration.plus(result.duration());
        }
        sb.append(String.format("%-16s %3s       %4d Likes  %6s%n", "Gesamt", "", totalLikes,
                formatDuration(totalDuration)));
        sb.append("===========================");
        logger.info(sb.toString());
    }

    private static String formatDuration(Duration duration) {
        return String.format("%d:%02d", duration.toMinutes(), duration.toSecondsPart());
    }

    private static boolean tryCheck(Check check, Page page) {
        try {
            check.actions().run(page, check.urls());
            return true;
        } catch (TimeoutError te) {
            logger.error("Timeout while processing provider check: {}", te.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error while processing provider check: {}", e.getMessage());
            return false;
        }
    }

    private record Check(FitnessCheckActions actions, List<String> urls) {
    }

    private record CheckResult(String provider, int urlCount, int likes, String status, Duration duration) {
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
                logger.debug("Invalid argument format: {}. Expected format: provider=url", arg);
            }
        }
        return providerMap;
    }
}
