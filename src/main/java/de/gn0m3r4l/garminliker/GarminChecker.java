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
    private static final String ACTIVITY_SUBPATH = "/app/activity/";
    private static final String PROFILE_SUBPATH = "app/profile";

    private static GarminChecker checker;

    /**
     * Constructor initializes the provider name for logging purposes.
     */
    public GarminChecker() {
        this.providerName = "Garmin";
    }

    void runUrl(WebDriver driver, String url) {

        int likeChain = 0;

        driver.get(url);
        pretendToBeHuman(1);

        if (!isProfilePage(driver)) {
            System.out.println("This is not a Garmin profile page. Please navigate to your profile page " +
                    "and try again. Make sure there is only one chrome debugger instance running.");
            return;
        }

        System.out.println("Garmin page detected");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TimeConstants.WEBDRIVER_TIMEOUT));

        var element = waitForMainPageById(driver, "pageContainer");

        List<WebElement> activityLinks = collectActivityLinks(element);

        for (int i = 0; i < activityLinks.size(); i++) {

            if (likeChain >= LIKE_CHAIN_LIMIT) {
                System.out.println("Detected " + likeChain + " already liked activities in a row. " +
                        "Assuming all activities are liked. Stopping further processing for this profile.");
                break;
            }

            System.out.println("Processing activity " + (i + 1) + " of " + activityLinks.size());
            var link = activityLinks.get(i);

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
    protected List<WebElement> collectActivityLinks(WebElement mainElement) {
        List<WebElement> result = new ArrayList<>();
        var links = mainElement.findElements(By.tagName("a"));
        for (WebElement l : links) {
            try {
                String href = l.getAttribute("href");
                if (href != null && href.contains(ACTIVITY_SUBPATH)) {
                    result.add(l);
                    if (result.size() >= MAX_ACTIVITIES_TO_PROCESS) {
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
    protected boolean isProfilePage(WebDriver driver) {
        String current = driver.getCurrentUrl();
        return current != null && current.contains(PROFILE_SUBPATH);
    }

    /**
     * Determine if an activity is already liked using button title or child icon heuristics.
     */
    protected boolean isActivityLiked(WebElement likeButton) {
        // check if activity is already liked
        return likeButton.findElements(new By.ByClassName("icon-heart-inverted")).isEmpty();
    }

    /**
     * Singleton getter for GarminChecker. Initializes the instance if not already done.
     * @return Singleton instance of GarminChecker
     */
    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new GarminChecker();
        }
        return checker;
    }

    protected void navigateBack(WebDriver driver) {
        driver.navigate().back();
        try {
            Thread.sleep(TimeConstants.MILLIS_PAUSE_AFTER_BACK_NAVIGATION);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
