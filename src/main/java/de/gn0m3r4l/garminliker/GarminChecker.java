package de.gn0m3r4l.garminliker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static de.gn0m3r4l.garminliker.Utils.pretendToBeHuman;

public class GarminChecker extends FitnessCheckerActionsImpl {
    private static final int LIKE_CHAIN_LIMIT = 3;
    private static final int MAX_ACTIVITIES_TO_PROCESS = 3;

    private static GarminChecker checker;

    public GarminChecker() {
        this.providerName = "Garmin";
    }

    void runUrl(WebDriver driver, String url) {

        int likeChain = 0;

        driver.get(url);
        pretendToBeHuman(1);

        if (!isProfilePage(driver, "app/profile")) {
            System.out.println("This is not a Garmin profile page. Please navigate to your profile page " +
                    "and try again. Make sure there is only one chrome debuger instance running.");
            return;
        }

        System.out.println("Garmin page detected");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TimeConstants.WEBDRIVER_TIMOUT));

        var element = waitForMainPageById(driver, "pageContainer");

        List<WebElement> acitivityLinks = collectActivityLinks(element, MAX_ACTIVITIES_TO_PROCESS, "/app/activity/");

        for (int i = 0; i < acitivityLinks.size(); i++) {

            if (likeChain >= LIKE_CHAIN_LIMIT) {
                System.out.println("Detected " + likeChain + " already liked activities in a row. " +
                        "Assuming all activities are liked. Stopping further processing for this profile.");
                break;
            }

            System.out.println("Processing activity " + (i + 1) + " of " + acitivityLinks.size());
            var link = acitivityLinks.get(i);

            wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.id("pageContainer")
                    )
            );

            String href = link.getAttribute("href");
            if (href == null) {
                System.out.println("Activity link has no href, skipping.");
                continue;
            }

            System.out.println("Navigating to activity: " + href);
            navigateToHref(driver, href);

            // simulate time for search a web page for a button
            pretendToBeHuman(1, TimeConstants.DELAY_AFTER_LIKE_IN_SECONDS);

            // wait for like button to be clickable
            WebElement likeElement = wait.until(
                    ExpectedConditions.elementToBeClickable(By.className("like"))
            );

            if (!isActivityLiked(likeElement)) {
                boolean ok = performLike(likeElement);
                if (ok) {
                    likeChain = 0;
                }
            } else {
                System.out.printf("Already liked activity (like chain: %s)%n", likeChain);
                likeChain++;
            }

            // Time to think like a human what to do next
            pretendToBeHuman(TimeConstants.HUMAN_THINKING_MIN_IN_SECONDS, TimeConstants.HUMAN_THINKING_MAX_IN_SECONDS);
            // This is necessary to go back to the profile page, as the link elements are not valid otherwise
            navigateBack(driver);
        }

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

    /**
     * Navigate to href with a small human-like pause. If href is null or empty, this is a no-op.
     */
    protected void navigateToHref(WebDriver driver, String href) {
        if (href == null || href.isEmpty()) {
            return;
        }
        driver.get(href);
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
    protected List<WebElement> collectActivityLinks(WebElement mainElement, int limit, String activityPathFragment) {
        List<WebElement> result = new ArrayList<>();
        var links = mainElement.findElements(By.tagName("a"));
        for (WebElement l : links) {
            try {
                String href = l.getAttribute("href");
                if (href != null && href.contains(activityPathFragment)) {
                    result.add(l);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return result;
    }

    /**
     * Null-safe check whether the current driver page is a profile page as indicated by marker substring.
     */
    protected boolean isProfilePage(WebDriver driver, String marker) {
        String current = driver.getCurrentUrl();
        return current != null && current.contains(marker);
    }

    /**
     * Determine if an activity is already liked using button title or child icon heuristics.
     */
    protected boolean isActivityLiked(WebElement likeButton) {
        // check if activity is already liked
        return likeButton.findElements(new By.ByClassName("icon-heart-inverted")).isEmpty();
    }

    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new GarminChecker();
        }
        return checker;
    }

    protected void navigateBack(WebDriver driver) {
        driver.navigate().back();
        try {
            Thread.sleep(TimeConstants.MILLIES_PAUSE_AFTER_BACK_NAVIGATION);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
