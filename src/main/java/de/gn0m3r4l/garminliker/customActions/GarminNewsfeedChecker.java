package de.gn0m3r4l.garminliker.customActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;

import de.gn0m3r4l.garminliker.actions.FitnessCheckActions;
import de.gn0m3r4l.garminliker.actions.FitnessCheckerActionsImpl;
import de.gn0m3r4l.garminliker.utils.TimeConstants;
import static de.gn0m3r4l.garminliker.utils.Utils.pretendToBeHuman;

/**
 * Liked Aktivitäten direkt im Garmin-Newsfeed (https://connect.garmin.com/app/newsfeed), gefiltert auf "Freunde".
 * <p>
 * Ablauf: Seite laden, Personen-Filter prüfen und – falls nicht exakt "Freunde" – setzen und den Feed neu laden.
 * Danach den Feed von oben nach unten abarbeiten und jede noch nicht gelikte Aktivität liken. Am Ende der gerenderten
 * Liste wird gescrollt und aktiv auf das Lazy Loading gewartet (Nachlade-Spinner weg, neue Einträge da).
 * Abbruch, sobald {@link #LIKE_CHAIN_LIMIT} bereits gelikte Aktivitäten in Folge gefunden wurden, nach
 * {@link #MAX_FAILED_LOADS} erfolglosen Nachladeversuchen (Feed-Ende) oder bei {@link #MAX_LIKES_PER_RUN}.
 * <p>
 * Der Feed ist eine virtualisierte Liste: Karten werden beim Scrollen aus dem DOM entfernt und neu erzeugt.
 * Deshalb werden Karten nie per Index, sondern immer über ihre Aktivitäts-ID adressiert.
 */
public class GarminNewsfeedChecker extends FitnessCheckerActionsImpl {

    public static final String NEWSFEED_SUBPATH = "/app/newsfeed";
    private static final Logger logger = LoggerFactory.getLogger(GarminNewsfeedChecker.class);

    private static final int LIKE_CHAIN_LIMIT = 5;
    private static final int MAX_LIKES_PER_RUN = 150;
    private static final int MAX_FAILED_LOADS = 3;
    private static final int LAZY_LOAD_TIMEOUT_MILLIS = 20_000;
    private static final int SCROLL_STEP_PX = 900;

    private static final String MAIN_PAGE_ID = "pageContainer";
    private static final String CARD_SELECTOR = "[class*='ActivityCard_activityContainer']";
    private static final String ACTIVITY_SUBPATH = "/app/activity/";
    private static final String ROW_SELECTOR = "[class*='VirtualizedList_virtualRow']";
    /**
     * Like-Button = erster Button im ersten Social-Button-Wrapper der Karte (zweiter Wrapper = Kommentar).
     * Bewusst nicht über das aria-label, denn das wechselt beim Liken von "Gefällt mir" auf "Gefällt nicht".
     */
    private static final String LIKE_BUTTON_SELECTOR = "[class*='CommentLikeSection_socialButtonWrapper'] button";
    private static final Pattern LIKED_LABEL = Pattern.compile("^\\s*(Gefällt nicht|Unlike)\\s*$");
    private static final Pattern UNLIKED_LABEL = Pattern.compile("^\\s*(Gefällt mir|Like)\\s*$");
    /** Timeout für Elemente, die durch die virtualisierte Liste jederzeit verschwinden können. */
    private static final int ELEMENT_TIMEOUT_MILLIS = 5_000;
    private static final int LIKE_CONFIRM_TIMEOUT_MILLIS = 5_000;
    private static final String FILTER_TRIGGER_SELECTOR = "[class*='NewsFeedFilter_comboboxTrigger']";
    private static final String FILTER_ITEM_SELECTOR = "label[class*='NewsFeedFilter_menuItem']";
    private static final Pattern FRIENDS_LABEL = Pattern.compile("^\\s*(Freunde|Friends)\\s*$");
    private static final Pattern ACTIVITY_ID = Pattern.compile("/app/activity/(\\d+)");

    /**
     * Liest den Like-Status aus den React-Props der Karte ({@code likedByUser}). Es werden die aktuelle Fiber und
     * ihre Alternate geprüft. Sobald irgendwo {@code true} steht, gilt die Aktivität als gelikt – ein Klick auf
     * einen gelikten Button würde den Like wieder entfernen, daher konservativ. Liefert {@code null}, wenn kein
     * Status gefunden wurde (z.B. nach einem Garmin-Update).
     */
    private static final String LIKED_STATE_JS = """
            b => {
              const key = Object.keys(b).find(k => k.startsWith('__reactFiber'));
              if (!key) return null;
              let found = null;
              for (const start of [b[key], b[key].alternate]) {
                let f = start;
                for (let i = 0; i < 20 && f; i++) {
                  const p = f.memoizedProps;
                  if (p && typeof p.likedByUser === 'boolean') {
                    if (p.likedByUser) return true;
                    found = false;
                    break;
                  }
                  f = f.return;
                }
              }
              return found;
            }
            """;

    private static final String ACTIVITY_HREFS_JS = """
            cards => cards.map(c => {
              const a = c.querySelector("a[href*='/app/activity/']");
              return a ? a.getAttribute('href') : null;
            })
            """;

    private static final String AUTHOR_JS = """
            c => [...c.querySelectorAll("a[href*='/app/profile/']")]
                   .map(a => a.innerText.trim()).find(t => t) || '?'
            """;

    /**
     * Status der Personen-Filter-Gruppe ("Alle", "Favoriten", "Freunde", "Nur ich"): Liste von {label, checked}.
     * Die Gruppe endet am ersten Divider des Menüs.
     */
    private static final String PEOPLE_FILTER_STATE_JS = """
            () => {
              const menu = document.querySelector("[class*='NewsFeedFilter_comboxBoxMenu']");
              if (!menu) return [];
              const result = [];
              for (const li of menu.querySelectorAll('li')) {
                if (String(li.className).includes('divider')) break;
                const input = li.querySelector('input[type=checkbox]');
                if (input) result.push({ label: li.textContent.trim(), checked: input.checked });
              }
              return result;
            }
            """;

    /**
     * Filter ist initialisiert, sobald irgendeine Checkbox der Personen-Gruppe gesetzt ist (die Gruppe
     * "Aktivitätstyp" zählt nicht – dort ist "Alle" schon vor dem Laden der Einstellung gesetzt).
     */
    private static final String FILTER_INITIALIZED_JS = "() => (" + PEOPLE_FILTER_STATE_JS.strip()
            + ")().some(e => e.checked)";

    /** Höchster data-index einer gerenderten Feed-Karte (Spinner-/Platzhalter-Zeilen zählen nicht). */
    private static final String MAX_CARD_INDEX_JS = """
            () => Math.max(-1, ...[...document.querySelectorAll("[class*='VirtualizedList_virtualRow']")]
                    .filter(r => r.querySelector("[class*='Card_card']"))
                    .map(r => Number(r.getAttribute('data-index'))))
            """;

    /** Lazy Loading fertig: neue Karte mit höherem data-index vorhanden und kein Nachlade-Spinner sichtbar. */
    private static final String LAZY_LOAD_DONE_JS = """
            prev => {
              const spinner = document.querySelector("[class*='VirtualizedList_fetchMoreDataSpinner']");
              const max = Math.max(-1, ...[...document.querySelectorAll("[class*='VirtualizedList_virtualRow']")]
                    .filter(r => r.querySelector("[class*='Card_card']"))
                    .map(r => Number(r.getAttribute('data-index'))));
              return !spinner && max > prev;
            }
            """;

    private static GarminNewsfeedChecker checker;

    private int likeChain;
    private int likesThisRun;

    /**
     * Constructor initializes the provider name for logging purposes.
     */
    public GarminNewsfeedChecker() {
        this.providerName = "Garmin Newsfeed";
    }

    /**
     * Singleton getter.
     *
     * @return Singleton instance of GarminNewsfeedChecker
     */
    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new GarminNewsfeedChecker();
        }
        return checker;
    }

    /**
     * @param url zu prüfende URL
     * @return true, wenn die URL auf den Garmin-Newsfeed zeigt
     */
    public static boolean isNewsfeedUrl(String url) {
        return url != null && url.contains(NEWSFEED_SUBPATH);
    }

    @Override
    protected void runUrl(Page page, String url) {
        likeChain = 0;
        likesThisRun = 0;

        openNewsfeed(page, url);
        if (!isNewsfeedUrl(page.url())) {
            logger.warn("Not on the Garmin newsfeed (current url: {}). Logged in?", page.url());
            return;
        }
        if (!ensureFriendsFilter(page, url)) {
            logger.error("Could not set newsfeed filter to 'Freunde'. Aborting to avoid liking the wrong feed.");
            return;
        }
        waitForFirstCard(page);
        processFeed(page);
        logger.info("Newsfeed done. Likes given in this run: {}", likesThisRun);
    }

    private void openNewsfeed(Page page, String url) {
        page.navigate(url);
        waitForMainPageById(page, MAIN_PAGE_ID);
        pretendToBeHuman(TimeConstants.DELAY_AFTER_PAGE_LOAD);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Filter
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Stellt sicher, dass im Personen-Filter ausschließlich "Freunde" aktiv ist. Es wird nichts vorausgesetzt:
     * Der aktuelle Zustand wird erst gelesen, nachdem Garmin die gespeicherte Filter-Einstellung geladen hat.
     * Wird der Filter geändert, wird der Feed neu geladen und der Zustand erneut geprüft.
     *
     * @return true, wenn danach nur "Freunde" aktiv ist
     */
    protected boolean ensureFriendsFilter(Page page, String url) {
        if (!waitForFilterInitialized(page)) {
            return false;
        }
        List<String> active = readActivePeopleFilters(page);
        if (isOnlyFriends(active)) {
            logger.debug("Filter 'Freunde' is active.");
            return true;
        }

        logger.info("Newsfeed people filter is {}, switching to 'Freunde'.", active);
        selectFriendsFilter(page);
        // Feed neu aufbauen, damit garantiert keine Karten des alten Filters mehr im DOM hängen
        openNewsfeed(page, url);
        if (!waitForFilterInitialized(page)) {
            return false;
        }
        active = readActivePeopleFilters(page);
        logger.info("Newsfeed people filter after switch: {}", active);
        return isOnlyFriends(active);
    }

    private boolean waitForFilterInitialized(Page page) {
        try {
            page.waitForFunction(FILTER_INITIALIZED_JS, null, new Page.WaitForFunctionOptions()
                    .setTimeout(TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR));
            return true;
        } catch (TimeoutError e) {
            logger.warn("Newsfeed filter state did not load within {}s.", TimeConstants.BROWSER_TIMEOUT);
            return false;
        }
    }

    /**
     * @return Labels der aktiven Einträge in der Personen-Filter-Gruppe
     */
    @SuppressWarnings("unchecked")
    protected List<String> readActivePeopleFilters(Page page) {
        List<String> active = new ArrayList<>();
        Object raw = page.evaluate(PEOPLE_FILTER_STATE_JS);
        if (!(raw instanceof List<?> items)) {
            return active;
        }
        for (Object item : items) {
            Map<String, Object> entry = (Map<String, Object>) item;
            if (Boolean.TRUE.equals(entry.get("checked"))) {
                active.add(String.valueOf(entry.get("label")));
            }
        }
        return active;
    }

    private static boolean isOnlyFriends(List<String> activeLabels) {
        return activeLabels.size() == 1 && FRIENDS_LABEL.matcher(activeLabels.getFirst()).matches();
    }

    private void selectFriendsFilter(Page page) {
        Locator trigger = page.locator(FILTER_TRIGGER_SELECTOR).first();
        trigger.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        trigger.click();
        pretendToBeHuman(1, 2);
        page.locator(FILTER_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(FRIENDS_LABEL)).first()
                .click();
        pretendToBeHuman(TimeConstants.DELAY_AFTER_PAGE_LOAD);
        if ("true".equals(trigger.getAttribute("aria-expanded"))) {
            trigger.click();
            pretendToBeHuman(1);
        }
    }

    private void waitForFirstCard(Page page) {
        page.locator(CARD_SELECTOR).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Feed
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Arbeitet den Feed blockweise ab: alle gerenderten, noch unbearbeiteten Karten verarbeiten, dann scrollen und
     * auf das Lazy Loading warten.
     */
    protected void processFeed(Page page) {
        Set<String> processed = new HashSet<>();
        int failedLoads = 0;

        while (!isDone() && failedLoads < MAX_FAILED_LOADS) {
            for (String activityId : collectNewActivityIds(page, processed)) {
                if (isDone()) {
                    break;
                }
                processed.add(activityId);
                processActivity(page, activityId);
            }
            if (isDone()) {
                break;
            }
            failedLoads = scrollAndWaitForMore(page) ? 0 : failedLoads + 1;
        }
        logStopReason(processed.size(), failedLoads);
    }

    private boolean isDone() {
        return likeChain >= LIKE_CHAIN_LIMIT || likesThisRun >= MAX_LIKES_PER_RUN;
    }

    private void logStopReason(int processedCount, int failedLoads) {
        if (likeChain >= LIKE_CHAIN_LIMIT) {
            logger.info("Detected {} already liked activities in a row. Stopping newsfeed.", likeChain);
        } else if (likesThisRun >= MAX_LIKES_PER_RUN) {
            logger.info("Reached max likes per run ({}). Stopping newsfeed.", MAX_LIKES_PER_RUN);
        } else if (failedLoads >= MAX_FAILED_LOADS) {
            logger.info("Nothing more loaded after {} attempts, end of feed reached.", failedLoads);
        }
        logger.debug("Processed {} activities in newsfeed.", processedCount);
    }

    /**
     * Scrollt bis zur letzten gerenderten Zeile (dadurch entsteht keine Lücke in der virtualisierten Liste) und
     * etwas darüber hinaus. Wartet dann, bis neue Karten gerendert bzw. per Lazy Loading nachgeladen sind und der
     * Nachlade-Spinner verschwunden ist.
     *
     * @return true, wenn neue Karten aufgetaucht sind
     */
    protected boolean scrollAndWaitForMore(Page page) {
        int maxIndexBefore = maxCardIndex(page);
        Locator rows = page.locator(ROW_SELECTOR);
        int count = rows.count();
        if (count > 0) {
            Locator last = rows.nth(count - 1);
            try {
                last.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions()
                        .setTimeout(ELEMENT_TIMEOUT_MILLIS));
                BoundingBox box = last.boundingBox(new Locator.BoundingBoxOptions()
                        .setTimeout(ELEMENT_TIMEOUT_MILLIS));
                if (box != null) {
                    page.mouse().move(box.x + box.width / 2, box.y + box.height / 2);
                }
            } catch (PlaywrightException e) {
                logger.debug("Last feed row vanished while scrolling: {}", e.getMessage());
            }
        }
        page.mouse().wheel(0, SCROLL_STEP_PX);

        try {
            page.waitForFunction(LAZY_LOAD_DONE_JS, maxIndexBefore,
                    new Page.WaitForFunctionOptions().setTimeout(LAZY_LOAD_TIMEOUT_MILLIS));
            logger.debug("Feed extended: max index {} -> {}", maxIndexBefore, maxCardIndex(page));
            pretendToBeHuman(1, 2);
            return true;
        } catch (TimeoutError e) {
            logger.debug("No new feed entries within {} ms (max index {}).", LAZY_LOAD_TIMEOUT_MILLIS, maxIndexBefore);
            return false;
        }
    }

    private int maxCardIndex(Page page) {
        try {
            Object result = page.evaluate(MAX_CARD_INDEX_JS);
            return result instanceof Number n ? n.intValue() : -1;
        } catch (PlaywrightException e) {
            return -1;
        }
    }

    /**
     * Liefert die Aktivitäts-IDs der aktuell gerenderten Karten in Feed-Reihenfolge, ohne bereits bearbeitete.
     */
    @SuppressWarnings("unchecked")
    protected List<String> collectNewActivityIds(Page page, Set<String> processed) {
        Set<String> result = new LinkedHashSet<>();
        try {
            List<Object> hrefs = (List<Object>) page.locator(CARD_SELECTOR).evaluateAll(ACTIVITY_HREFS_JS);
            for (Object href : hrefs) {
                String id = extractActivityId(href);
                if (id != null && !processed.contains(id)) {
                    result.add(id);
                }
            }
        } catch (PlaywrightException e) {
            logger.debug("Could not read feed cards: {}", e.getMessage());
        }
        return new ArrayList<>(result);
    }

    private static String extractActivityId(Object href) {
        if (!(href instanceof String s)) {
            return null;
        }
        Matcher m = ACTIVITY_ID.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Einzelne Aktivität
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Verarbeitet eine einzelne Karte: Status lesen, ggf. liken, likeChain pflegen.
     */
    protected void processActivity(Page page, String activityId) {
        Locator card = findCard(page, activityId);
        if (!scrollCardIntoView(card, activityId)) {
            return;
        }
        pretendToBeHuman(1, 2);

        String author = readAuthor(card);
        Locator likeButton = card.locator(LIKE_BUTTON_SELECTOR).first();
        if (likeButton.count() == 0) {
            logger.debug("No like button for activity {} ({}), skipping.", activityId, author);
            return;
        }

        Boolean liked = readLikedState(likeButton);
        if (liked == null || liked) {
            likeChain++;
            logger.debug("{} activity {} ({}), like chain: {}",
                    liked == null ? "Unknown like state for" : "Already liked", activityId, author, likeChain);
            return;
        }

        logger.info("Liking activity {} ({})", activityId, author);
        if (performLike(likeButton)) {
            likeChain = 0;
            likesThisRun++;
            verifyLike(card, activityId);
            pretendToBeHuman(TimeConstants.HUMAN_THINKING_MIN_IN_SECONDS / 2,
                    TimeConstants.HUMAN_THINKING_MAX_IN_SECONDS / 2);
        }
    }

    private Locator findCard(Page page, String activityId) {
        return page.locator(CARD_SELECTOR)
                .filter(new Locator.FilterOptions()
                        .setHas(page.locator("a[href*='" + ACTIVITY_SUBPATH + activityId + "']")))
                .first();
    }

    /**
     * @return false, wenn die Karte nicht (mehr) gerendert ist
     */
    private boolean scrollCardIntoView(Locator card, String activityId) {
        try {
            if (card.count() == 0) {
                logger.debug("Activity {} no longer rendered, skipping.", activityId);
                return false;
            }
            card.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions()
                    .setTimeout(ELEMENT_TIMEOUT_MILLIS));
            return true;
        } catch (PlaywrightException e) {
            logger.debug("Activity {} vanished while scrolling, skipping: {}", activityId, e.getMessage());
            return false;
        }
    }

    private String readAuthor(Locator card) {
        try {
            return String.valueOf(card.evaluate(AUTHOR_JS, null,
                    new Locator.EvaluateOptions().setTimeout(ELEMENT_TIMEOUT_MILLIS)));
        } catch (PlaywrightException e) {
            return "?";
        }
    }

    /**
     * Like-Status aus aria-label ("Gefällt mir" / "Gefällt nicht") und React-Prop {@code likedByUser}.
     *
     * @return true = gelikt, false = nicht gelikt, null = unbekannt
     */
    protected Boolean readLikedState(Locator likeButton) {
        try {
            String label = likeButton.getAttribute("aria-label",
                    new Locator.GetAttributeOptions().setTimeout(ELEMENT_TIMEOUT_MILLIS));
            Object fiberState = likeButton.evaluate(LIKED_STATE_JS, null,
                    new Locator.EvaluateOptions().setTimeout(ELEMENT_TIMEOUT_MILLIS));
            return combineLikedState(label, fiberState);
        } catch (PlaywrightException e) {
            logger.debug("Could not read like state: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Konservativ: gelikt, sobald eine Quelle "gelikt" sagt; nicht gelikt nur bei eindeutigem Label.
     */
    static Boolean combineLikedState(String label, Object fiberState) {
        boolean labelLiked = label != null && LIKED_LABEL.matcher(label).matches();
        if (labelLiked || Boolean.TRUE.equals(fiberState)) {
            return true;
        }
        boolean labelUnliked = label != null && UNLIKED_LABEL.matcher(label).matches();
        return labelUnliked ? Boolean.FALSE : null;
    }

    /**
     * Wartet bis zu {@link #LIKE_CONFIRM_TIMEOUT_MILLIS}, bis der Button der Karte auf "Gefällt nicht" wechselt.
     */
    private void verifyLike(Locator card, String activityId) {
        try {
            card.locator("button[aria-label='Gefällt nicht'], button[aria-label='Unlike']").first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(LIKE_CONFIRM_TIMEOUT_MILLIS));
        } catch (PlaywrightException e) {
            logger.warn("Like for activity {} could not be confirmed within {} ms (rate limit?).",
                    activityId, LIKE_CONFIRM_TIMEOUT_MILLIS);
        }
    }
}
