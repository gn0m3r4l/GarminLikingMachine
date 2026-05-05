package de.gn0m3r4l.garminliker.customActions;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import de.gn0m3r4l.garminliker.actions.FitnessCheckActions;
import de.gn0m3r4l.garminliker.actions.FitnessCheckerActionsImpl;
import de.gn0m3r4l.garminliker.utils.TimeConstants;
import de.gn0m3r4l.garminliker.utils.Utils;
import static de.gn0m3r4l.garminliker.utils.Utils.pretendToBeHuman;

public class GarminChecker extends FitnessCheckerActionsImpl {
    private static final Logger logger = LoggerFactory.getLogger(GarminChecker.class);
    public static final String GARMIN_MAIN_PAGE_ID = "pageContainer";
    private static final int LIKE_CHAIN_LIMIT = 3;
    private static final int MAX_ACTIVITIES_TO_PROCESS = 5;
    private static final String ACTIVITY_SUBPATH = "/app/activity/";
    private static final String PROFILE_SUBPATH = "app/profile";
    private static final String GARMIN_BASE = "https://connect.garmin.com";
    private static GarminChecker checker;

    /**
     * Constructor initializes the provider name for logging purposes.
     */
    public GarminChecker() {
        this.providerName = "Garmin";
    }

    /**
     * Singleton getter for GarminChecker. Initializes the instance if not already done.
     *
     * @return Singleton instance of GarminChecker
     */
    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new GarminChecker();
        }
        return checker;
    }

    protected void runUrl(Page page, String url) {

        int likeChain = 0;

        page.navigate(url);
        waitForMainPageById(page, GARMIN_MAIN_PAGE_ID);
        pretendToBeHuman(1);

        if (!isProfilePage(page)) {
            logger.debug("This is not a Garmin profile page. Please navigate to your profile page " +
                    "and try again. Make sure there is only one chrome debugger instance running.");
            return;
        }

        logger.debug("Garmin page detected");
        String profileUrl = page.url();

        waitForMainPageById(page, GARMIN_MAIN_PAGE_ID);
        List<String> activityLinks = collectActivityLinks(page.locator("#pageContainer"));

        for (int i = 0; i < activityLinks.size(); i++) {

            if (likeChain >= LIKE_CHAIN_LIMIT) {
                logger.debug("Detected {} already liked activities in a row. " +
                        "Assuming all activities are liked. Stopping further processing for this profile.", likeChain);
                break;
            }

            logger.debug("Processing activity {} of {}", (i + 1), activityLinks.size());
            String href = activityLinks.get(i);
            if (href == null) {
                logger.debug("Activity link has no href, skipping.");
                continue;
            }

            logger.info("Navigating to activity: {}", href);
            navigateToHref(page, href);

            // simulate time for search a web page for a button
            pretendToBeHuman(1, TimeConstants.DELAY_AFTER_LIKE_IN_SECONDS);

            Locator likeElement = page.locator(".like").first();
            likeElement.waitFor(new Locator.WaitForOptions().setTimeout(
                    TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR));

            if (!isActivityLiked(likeElement)) {
                boolean ok = performLike(likeElement);
                if (ok) {
                    likeChain = 0;
                }
            } else {
                logger.debug("Already liked activity (like chain: {})", likeChain);
                likeChain++;
            }

            // Time to think like a human what to do next
            pretendToBeHuman(TimeConstants.HUMAN_THINKING_MIN_IN_SECONDS, TimeConstants.HUMAN_THINKING_MAX_IN_SECONDS);
            // This is necessary to go back to the profile page, as the link elements are not valid otherwise
            navigateBack(page, profileUrl);
            waitForMainPageById(page, GARMIN_MAIN_PAGE_ID);
        }

    }

    /**
     * Navigate to href with a small human-like pause. If href is null or empty, this is a no-op.
     */
    protected void navigateToHref(Page page, String href) {
        if (href == null || href.isEmpty()) {
            return;
        }
        page.navigate(GARMIN_BASE + href);
        // small post navigation wait to let the page load a bit
        try {
            Thread.sleep(TimeConstants.MILLIS_BETWEEN_SCROLL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // mimic human
        Utils.pretendToBeHuman(1);
    }

    /**
     * Collect link elements from a main container that contain the provided activityPathFragment in their href.
     * Returns at most `limit` elements and filters out null hrefs.
     */
    protected List<String> collectActivityLinks(Locator mainElement) {
        List<String> result = new java.util.ArrayList<>();
        Locator links = mainElement.locator("a");
        int count = links.count();
        for (int i = 0; i < count; i++) {
            String href = links.nth(i).getAttribute("href");
            if (href != null && href.contains(ACTIVITY_SUBPATH)) {
                result.add(href);
                if (result.size() >= MAX_ACTIVITIES_TO_PROCESS) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Null-safe check whether the current driver page is a profile page as indicated by marker substring.
     */
    protected boolean isProfilePage(Page page) {
        String current = page.url();
        return current != null && current.contains(PROFILE_SUBPATH);
    }

    /**
     * Determine if an activity is already liked using button title or child icon heuristics.
     */
    protected boolean isActivityLiked(Locator likeButton) {
        // check if activity is already liked
        try {
            return likeButton.locator(".icon-heart-inverted").count() == 0;
        } catch (PlaywrightException ignored) {
            return true;
        }
    }

    protected void navigateBack(Page page, String profileUrl) {
        if (profileUrl == null || profileUrl.isEmpty()) {
            return;
        }
        page.navigate(profileUrl);
        try {
            Thread.sleep(TimeConstants.MILLIS_PAUSE_AFTER_NAVIGATION);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
