package de.gn0m3r4l.garminliker.customActions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import de.gn0m3r4l.garminliker.actions.FitnessCheckActions;
import de.gn0m3r4l.garminliker.actions.FitnessCheckerActionsImpl;
import de.gn0m3r4l.garminliker.utils.TimeConstants;
import de.gn0m3r4l.garminliker.utils.Utils;
import static de.gn0m3r4l.garminliker.utils.Utils.pretendToBeHuman;

public class StravaChecker extends FitnessCheckerActionsImpl {
    private static final int LIKE_CHAIN_LIMIT = 8;
    public static final int TITLE_MAX_LEN = 10;
    private static StravaChecker checker;

    /**
     * Constructor initializes the provider name for logging purposes.
     */
    public StravaChecker() {
        this.providerName = "Strava";
    }

    protected void runUrl(Page page, String stravaUrl) {
        int likeChain = 0;

        page.navigate(stravaUrl);
        pretendToBeHuman(1);

        var element = waitForMainPageById(page, "main");

        List<String> activityIds = collectActivityIds(element);

        String[] skip = new String[]{"strava club", "daniel p"};

        for (var activityId : activityIds) {

            if (likeChain >= LIKE_CHAIN_LIMIT) {
                System.out.println("Detected " + likeChain + " already liked activities in a row. Assuming " +
                        "all activities are liked. Stopping further processing for this profile.");
                break;
            }

            likeChain = processActivity(page, activityId, skip, Duration.ofSeconds(TimeConstants.BROWSER_TIMEOUT),
                    likeChain);
        }
    }

    /**
     * Process a single activity by id: find div, wait for load, optional skip, find like button and handle it.
     * Returns updated likeChain value.
     */
    private int processActivity(Page page, String activityId, String[] skipSubstrings,
                                Duration timeout, int likeChain) {
        Locator activityDiv = findActivityDiv(page, activityId);
        if (activityDiv == null) {
            return likeChain;
        }

        boolean loaded = waitForStravaActivityDivLoaded(activityDiv, timeout);
        if (!loaded) {
            return likeChain;
        }

        Utils.pretendToBeHuman(1, 2);

        if (shouldSkipActivity(activityDiv, skipSubstrings)) {
            return likeChain;
        }

        Locator possibleLikeButton = findLikeButton(activityDiv);
        return handleLikeButton(possibleLikeButton, activityId, likeChain);
    }

    /**
     * Handle like button decision logic and return updated likeChain value.
     * Encapsulates title inspection and uses performLike for clicking.
     */
    protected int handleLikeButton(Locator likeButton, String activityId, int likeChain) {
        if (likeButton == null) {
            return likeChain;
        }

        String title;
        try {
            title = likeButton.getAttribute("title");
        } catch (PlaywrightException e) {
            System.out.println("Like button became unavailable for activity " + activityId + ", skipping.");
            return likeChain + 1;
        }

        if (title == null) {
            System.out.println("Like button has no title for activity " + activityId + ", skipping status check.");
            return likeChain + 1;
        }

        String lc = title.toLowerCase();
        if (lc.contains("ansehen")) {
            likeChain++;
            System.out.printf("Already liked activity %s (like chain: %s)%n", activityId, likeChain);
            return likeChain;
        }

        if (lc.contains("geben") || lc.contains("sei der erste")) {
            boolean ok = performLike(likeButton);
            return ok ? 0 : likeChain + 1;
        }

        System.out.println("Could not determine like status for activity " + activityId);
        return likeChain + 1;
    }

    /**
     * Heuristic to decide whether to skip an activity (e.g. club or own posts) by checking anchor texts.
     * Returns true if any skipSubstring is contained in any anchor text (case-insensitive).
     */
    protected boolean shouldSkipActivity(Locator activityDiv, String[] skipSubstrings) {
        if (activityDiv == null || skipSubstrings == null || skipSubstrings.length == 0) {
            return false;
        }
        Locator anchors = activityDiv.locator("a");
        int count = anchors.count();
        for (int i = 0; i < count; i++) {
            if (anchorContainsSkip(anchors.nth(i), skipSubstrings)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check a single anchor element for any of the skip substrings. Extracted to reduce branching.
     */
    protected boolean anchorContainsSkip(Locator anchor, String[] skipSubstrings) {
        try {
            String text = anchor.innerText();
            String lc = text.toLowerCase();
            for (String s : skipSubstrings) {
                if (s != null && lc.contains(s.toLowerCase())) {
                    return true;
                }
            }
        } catch (PlaywrightException ignored) {
        }
        return false;
    }

    /**
     * Wait until a div with the given id is populated / loaded (heuristic based on text length or children).
     */
    protected boolean waitForStravaActivityDivLoaded(Locator activityDiv, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                activityDiv.scrollIntoViewIfNeeded();
                String text = activityDiv.innerText();
                if (text.trim().length() > TITLE_MAX_LEN) {
                    return true;
                }
                if (activityDiv.locator("a, button, img, span").count() > 0) {
                    return true;
                }
            } catch (PlaywrightException ignored) {
            }

            try {
                Thread.sleep(TimeConstants.MILLIS_PAUSE_AFTER_NAVIGATION);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Safely attempt to find an activity div by id; returns null if not found.
     */
    protected Locator findActivityDiv(Page page, String activityId) {
        Locator locator = page.locator("#" + activityId).first();
        return locator.count() == 0 ? null : locator;
    }

    /**
     * Singleton accessor for StravaChecker instance with lazy initialization.
     *
     * @return Singleton instance of StravaChecker
     */
    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new StravaChecker();
        }
        return checker;
    }

    /**
     * Collect div ids that represent activity entries (e.g. Strava feed entries).
     * Never returns null: returns empty list if none found.
     */
    protected List<String> collectActivityIds(Locator element) {
        List<String> activityIds = new ArrayList<>();
        Locator allDivs = element.locator("div[id]");
        int count = allDivs.count();
        for (int i = 0; i < count; i++) {
            try {
                String id = allDivs.nth(i).getAttribute("id");
                if (id != null && id.startsWith("feed-entry")) {
                    activityIds.add(id);
                }
            } catch (PlaywrightException ignored) {
            }
        }
        return activityIds;
    }

    /**
     * Try to find a like/kudos button inside an activity container.
     * Returns null if none found.
     */
    private Locator findLikeButton(Locator activityDiv) {
        try {
            Locator buttons = activityDiv.locator("button");
            int count = buttons.count();
            for (int i = 0; i < count; i++) {
                Locator button = buttons.nth(i);
                String title = button.getAttribute("title");
                if (title != null && title.toLowerCase().contains("kudos")) {
                    return button;
                }
                String cls = button.getAttribute("class");
                if (cls != null && cls.toLowerCase().contains("like")) {
                    return button;
                }
            }
        } catch (PlaywrightException ignored) {
        }
        return null;
    }
}
