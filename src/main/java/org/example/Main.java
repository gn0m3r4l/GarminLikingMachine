package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main {

    private static List<String> listOfUserURLs = new ArrayList<>();

    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "localhost:9222");
        WebDriver driver = new ChromeDriver(options);

        if (listOfUserURLs == null || listOfUserURLs.isEmpty()) {
            System.out.println("Profile list is empty. Starting with the current page.");
            listOfUserURLs = List.of(Objects.requireNonNull(driver.getCurrentUrl()));
        }

        for (var url : listOfUserURLs) {
            runCheck(driver, url);
        }

        // exit to free ressources (chrome-driver is greedy)
        driver.quit();
        System.exit(0);
    }

    private static void runCheck(WebDriver driver, String url) {

        driver.get(url);
        pretendToBeHuman(1, 1);

        // getting current URL and checking if it's Garmin profile page (the check could be more detailed, but would then also be more prone to errors if the URL structure changes)
        if (driver.getCurrentUrl().contains("app/profile")) {
            System.out.println("Garmin page detected");

            // defining timeout for waiting
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // waiting for main page container to be visible
            WebElement element = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.id("pageContainer")
                    )
            );

            // collecting all links in main container and filter for activity links
            var links = element.findElements(new By.ByTagName("a"));
            var acitivityLinks = links.stream().filter(
                    webElement ->
                            webElement.getAttribute("href") != null
                                    && webElement.getAttribute("href")
                                    .contains("/app/activity/")).toList();

            // loop over activites
            for (int i = 0; i < acitivityLinks.size(); i++) {
                System.out.println("Processing activity " + (i + 1) + " of " + acitivityLinks.size());
                var link = acitivityLinks.get(i);
                // again waiting for main container. Will be necessary after navigating to the next page
                wait.until(
                        ExpectedConditions.visibilityOfElementLocated(
                                By.id("pageContainer")
                        )
                );

                System.out.println("Navigating to activity: " + link.getAttribute("href"));
                // go to activity page
                driver.get(link.getAttribute("href"));
                // here no random as we only want the page to be loaded
                pretendToBeHuman(2, 2);

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
                } else {
                    System.out.println("Activity already liked");
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

    /**
     * Simple method to mimic human behavior by sleeping a random time between minSeconds and maxSeconds.
     * Application crashes when sleep is interrupted.
     *
     * @param minSeconds Minimum seconds to sleep
     * @param maxSeconds Maximum seconds to sleep
     */
    private static void pretendToBeHuman(int minSeconds, int maxSeconds) {
        int sleepTime = (int) ((Math.random() * (maxSeconds - minSeconds) + minSeconds) * 1000);
        System.out.println("Sleeping for " + sleepTime + " milliseconds to mimic human behavior.");
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            System.err.println("If this goes wrong, blame the interrupt.");
            System.exit(1);
        }
    }


}