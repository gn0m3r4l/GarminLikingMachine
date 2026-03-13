package de.gn0m3r4l.garminliker;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static de.gn0m3r4l.garminliker.Utils.pretendToBeHuman;

public class StravaChecker extends FitnessCheckerActionsImpl {
    private final int LIKE_CHAIN_LIMIT = 15;
    private static StravaChecker checker;

    public StravaChecker() {
        this.providerName = "Strava";
    }

    void runUrl(WebDriver driver, String stravaUrl) {
        int likeChain = 0;

        driver.get(stravaUrl);
        pretendToBeHuman(1);

        WebElement element = waitForMainPageById(driver, "main");

        List<String> activityIds = collectActivityIds(element);

        for (var activityId : activityIds) {

            if (likeChain >= LIKE_CHAIN_LIMIT) {
                System.out.println("Detected " + likeChain + " already liked activities in a row. Assuming all activities are liked. Stopping further processing for this profile.");
                break;
            }

            // try to re-find the div by id to avoid stale element references
            WebElement activityDiv;
            try {
                activityDiv = driver.findElement(By.id(activityId));
            } catch (NoSuchElementException e) {
                // div disappeared, skip
                continue;
            }

            // scroll to the div and wait until it is populated (has text or child elements)
            boolean loaded = waitForDivLoaded(driver, activityId, Duration.ofSeconds(10));
            if (!loaded) {
                System.out.println("Activity " + activityId + " did not populate in time, skipping.");
                continue;
            }
            else {
                pretendToBeHuman(1,2);
            }

            // skip certain profiles/clubs - evaluate safely
            boolean isStrava = false;
            try {
                var anchors = activityDiv.findElements(By.tagName("a"));
                for (WebElement a : anchors) {
                    try {
                        String text = a.getText();
                        String lc = text.toLowerCase();
                        if (lc.contains("strava club") || lc.contains("daniel p")) {
                            isStrava = true;
                            break;
                        }
                    } catch (StaleElementReferenceException ignored) {
                    }
                }
            } catch (StaleElementReferenceException ignored) {
            }
            if (isStrava) {
                continue;
            }

            System.out.println("Processing activity " + activityId);

            List<WebElement> buttons;
            try {
                buttons = activityDiv.findElements(By.tagName("button"));
            } catch (StaleElementReferenceException e) {
                continue;
            }

            WebElement likeButton = null;
            for (WebElement btn : buttons) {
                try {
                    String title = btn.getAttribute("title");
                    if (title != null && title.toLowerCase().contains("kudos")) {
                        likeButton = btn;
                        break;
                    }
                } catch (StaleElementReferenceException ignored) {
                }
            }

            if (likeButton != null) {
                String title;
                try {
                    title = likeButton.getAttribute("title");
                } catch (StaleElementReferenceException e) {
                    System.out.println("Like button became stale for activity " + activityId + ", skipping.");
                    continue;
                }

                if (title == null) {
                    System.out.println("Like button has no title for activity " + activityId + ", skipping status check.");
                } else if (title.toLowerCase().contains("ansehen")) {
                    // kudo already given
                    likeChain++;
                    System.out.printf("Already liked activity %s (like chain: %s)%n",activityId, likeChain);
                } else if (title.toLowerCase().contains("geben") || title.toLowerCase().contains("sei der erste")) {
                    try {
                        likeButton.click();
                        this.givenLikes++;
                        likeChain = 0;
                        System.out.println("Liked activity " + activityId + ". Total likes given: " + this.givenLikes);
                        pretendToBeHuman(1, 3);
                    } catch (StaleElementReferenceException e) {
                        System.out.println("Button went stale during click for activity " + activityId);
                    }
                } else {
                    System.out.println("Could not determine like status for activity " + activityId);
                }

            } else {
                System.out.println("Could not find like button for activity " + activityId);
            }

        }

    }

    private static List<String> collectActivityIds(WebElement element) {
        // collect div ids (avoid holding WebElement references which may go stale)
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



    private static boolean waitForDivLoaded(WebDriver driver, String activityId, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        try {
            return wait.until(drv -> {
                try {
                    WebElement el = drv.findElement(By.id(activityId));
                    // scroll into view
                    ((JavascriptExecutor) drv).executeScript("arguments[0].scrollIntoView({behavior:'instant', block:'center', inline:'center'});", el);

                    // quick heuristics: text length or presence of child actionable elements
                    String txt = el.getText();
                    if (txt.trim().length() > 10) {
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

    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new StravaChecker();
        }
        return checker;
    }

}
