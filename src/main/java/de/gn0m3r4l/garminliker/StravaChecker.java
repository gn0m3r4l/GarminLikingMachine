package de.gn0m3r4l.garminliker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import static de.gn0m3r4l.garminliker.Utils.pretendToBeHuman;

public class StravaChecker extends FitnessCheckerActionsImpl {
    private static final int LIKE_CHAIN_LIMIT = 8;
    public static final int TITLE_MAX_LEN = 10;
    private static StravaChecker checker;

    public StravaChecker() {
        this.providerName = "Strava";
    }

    void runUrl(WebDriver driver, String stravaUrl) {
        int likeChain = 0;

        driver.get(stravaUrl);
        pretendToBeHuman(1);

        var element = waitForMainPageById(driver, "main");

        List<String> activityIds = collectActivityIds(element);

        String[] skip = new String[]{"strava club", "daniel p"};

        for (var activityId : activityIds) {

            if (likeChain >= LIKE_CHAIN_LIMIT) {
                System.out.println("Detected " + likeChain + " already liked activities in a row. Assuming " +
                        "all activities are liked. Stopping further processing for this profile.");
                break;
            }

            likeChain = processActivity(driver, activityId, skip, Duration.ofSeconds(TimeConstants.WEBDRIVER_TIMOUT),
                    likeChain);
        }

    }

    /**
     * Process a single activity by id: find div, wait for load, optional skip, find like button and handle it.
     * Returns updated likeChain value.
     */
    private int processActivity(WebDriver driver, String activityId, String[] skipSubstrings, Duration timeout,
                                int likeChain) {
        WebElement activityDiv = findActivityDiv(driver, activityId);
        if (activityDiv == null) {
            return likeChain;
        }

        boolean loaded = waitForStravaActivityDivLoaded(driver, activityId, timeout);
        if (!loaded) {
            return likeChain;
        }

        // small human-like pause
        Utils.pretendToBeHuman(1, 2);

        if (shouldSkipActivity(activityDiv, skipSubstrings)) {
            return likeChain;
        }

        WebElement possibleLikeButton = findLikeButton(activityDiv);
        return handleLikeButton(driver, possibleLikeButton, activityId, likeChain);
    }

    /**
     * Handle like button decision logic and return updated likeChain value.
     * Encapsulates title inspection and uses performLike for clicking.
     */
    protected int handleLikeButton(WebDriver driver, WebElement likeButton, String activityId, int likeChain) {
        if (likeButton == null) {
            return likeChain;
        }

        String title;
        try {
            title = likeButton.getAttribute("title");
        } catch (StaleElementReferenceException e) {
            System.out.println("Like button became stale for activity " + activityId + ", skipping.");
            return likeChain + 1;
        }

        if (title == null) {
            System.out.println("Like button has no title for activity " + activityId + ", skipping status check.");
            return likeChain + 1;
        }

        String lc = title.toLowerCase();
        if (lc.contains("ansehen")) {
            // already liked
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
    protected boolean shouldSkipActivity(WebElement activityDiv, String[] skipSubstrings) {
        if (activityDiv == null || skipSubstrings == null || skipSubstrings.length == 0) {
            return false;
        }
        try {
            var anchors = activityDiv.findElements(By.tagName("a"));
            for (WebElement a : anchors) {
                if (anchorContainsSkip(a, skipSubstrings)) {
                    return true;
                }
            }
        } catch (StaleElementReferenceException ignored) {
        }
        return false;
    }

    /**
     * Check a single anchor element for any of the skip substrings. Extracted to reduce branching.
     */
    protected boolean anchorContainsSkip(WebElement anchor, String[] skipSubstrings) {
        try {
            String text = anchor.getText();
            if (text == null) {
                return false;
            }
            String lc = text.toLowerCase();
            for (String s : skipSubstrings) {
                if (s != null && lc.contains(s.toLowerCase())) {
                    return true;
                }
            }
        } catch (StaleElementReferenceException ignored) {
        }
        return false;
    }

    /**
     * Wait until a div with the given id is populated / loaded (heuristic based on text length or children).
     */
    protected boolean waitForStravaActivityDivLoaded(WebDriver driver, String activityId, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        try {
            return wait.until(drv -> {
                try {
                    WebElement el = drv.findElement(By.id(activityId));
                    // scroll into view
                    ((JavascriptExecutor) drv).executeScript("arguments[0].scrollIntoView({behavior:'instant', " +
                            "block:'center', inline:'center'});", el);

                    // quick heuristics: text length or presence of child actionable elements
                    String txt = el.getText();
                    if (txt != null && txt.trim().length() > TITLE_MAX_LEN) {
                        return true;
                    }
                    List<WebElement> children = el.findElements(By.cssSelector("a, button, img, span"));
                    return !children.isEmpty();
                } catch (StaleElementReferenceException | NoSuchElementException e) {
                    return false;
                }
            });
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    /**
     * Safely attempt to find an activity div by id; returns null if not found or stale.
     */
    protected WebElement findActivityDiv(WebDriver driver, String activityId) {
        try {
            return driver.findElement(By.id(activityId));
        } catch (NoSuchElementException | StaleElementReferenceException e) {
            return null;
        }
    }

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
    protected List<String> collectActivityIds(WebElement element) {
        List<String> activityIds = new ArrayList<>();
        List<WebElement> allDivs = element.findElements(By.tagName("div"));
        for (WebElement div : allDivs) {
            try {
                String id = div.getAttribute("id");
                if (id != null && id.startsWith("feed-entry")) {
                    activityIds.add(id);
                }
            } catch (StaleElementReferenceException ignored) {
                // skip stale elements
            }
        }
        return activityIds;
    }

    /**
     * Try to find a like/kudos button inside an activity container.
     * Returns null if none found (or stale).
     */
    private WebElement findLikeButton(WebElement activityDiv) {
        try {
            List<WebElement> buttons = activityDiv.findElements(By.tagName("button"));
            for (WebElement btn : buttons) {
                try {
                    String title = btn.getAttribute("title");
                    if (title != null && title.toLowerCase().contains("kudos")) {
                        return btn;
                    }
                    String cls = btn.getAttribute("class");
                    if (cls != null && cls.toLowerCase().contains("like")) {
                        return btn;
                    }
                } catch (StaleElementReferenceException ignored) {
                }
            }
        } catch (StaleElementReferenceException ignored) {
        }
        return null;
    }
}
