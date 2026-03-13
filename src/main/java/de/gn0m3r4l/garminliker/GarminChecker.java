package de.gn0m3r4l.garminliker;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static de.gn0m3r4l.garminliker.Utils.pretendToBeHuman;

public class GarminChecker extends FitnessCheckerActionsImpl {
    private static final int LIKE_CHAIN_LIMIT = 3;
    private static final int MAX_ACTIVITIES_TO_PROCESS = 4;
    private static GarminChecker checker;

    public GarminChecker() {
        this.providerName = "Garmin";
    }

    void runUrl(WebDriver driver, String url) {

        int likeChain = 0;

        driver.get(url);
        pretendToBeHuman(1);

        // getting current URL and checking if it's Garmin profile page (the check could be more detailed, but would then also be more prone to errors if the URL structure changes)
        String current = driver.getCurrentUrl();
        if (current != null && current.contains("app/profile")) {
            System.out.println("Garmin page detected");

            // defining timeout for waiting
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            var element = waitForMainPageById(driver, "pageContainer");

            // collecting all links in main container and filter for activity links
            var links = element.findElements(new By.ByTagName("a"));
            var acitivityLinks = links.stream().filter(
                            webElement -> {
                                String href = webElement.getAttribute("href");
                                return href != null && href.contains("/app/activity/");
                            })
                    .limit(MAX_ACTIVITIES_TO_PROCESS)
                    .toList();

            // loop over activites
            for (int i = 0; i < acitivityLinks.size(); i++) {

                if (likeChain >= LIKE_CHAIN_LIMIT) {
                    System.out.println("Detected " + likeChain + " already liked activities in a row. Assuming all activities are liked. Stopping further processing for this profile.");
                    break;
                }

                System.out.println("Processing activity " + (i + 1) + " of " + acitivityLinks.size());
                var link = acitivityLinks.get(i);
                // again waiting for main container. Will be necessary after navigating to the next page
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
                // go to activity page
                driver.get(href);
                // here no random as we only want the page to be loaded
                pretendToBeHuman(2);

                // simulate time for search a web page for a button
                pretendToBeHuman(1, 3);

                // wait for like button to be clickable
                WebElement likeElement = wait.until(
                        ExpectedConditions.elementToBeClickable(By.className("like"))
                );

                // check if activity is already liked
                if (!likeElement.findElements(new By.ByClassName("icon-heart-inverted")).isEmpty()) {
                    System.out.println("Liking activity");
                    likeElement.click();
                    this.givenLikes++;
                    System.out.println("Total likes given so far: " + this.givenLikes);
                    likeChain = 0;
                } else {
                    System.out.printf("Already liked activity (like chain: %s)%n", likeChain);
                    likeChain++;
                }

                // Time to think like a human what to do next
                pretendToBeHuman(5, 10);
                // This is necessary to go back to the profile page, as the link elements are not valid otherwise
                driver.navigate().back();
            }
        } else {
            System.out.println("This is not a Garmin profile page. Please navigate to your profile page and try again. Make sure there is only one chrome debuger instance running.");
        }

    }

    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new GarminChecker();
        }
        return checker;
    }


}
