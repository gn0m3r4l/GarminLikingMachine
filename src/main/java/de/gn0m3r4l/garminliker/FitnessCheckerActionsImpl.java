package de.gn0m3r4l.garminliker;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public abstract class FitnessCheckerActionsImpl implements FitnessCheckActions {

    protected String providerName;
    protected int givenLikes = 0;

    @Override
    public void run(WebDriver driver, List<String> urlsToBeCheckedSameWay) {

        if (urlsToBeCheckedSameWay == null || urlsToBeCheckedSameWay.isEmpty()) {
            System.out.printf("No URLs provided for %s, skipping.%n", providerName);
            return;
        }

        System.out.printf("Processing %s%n", providerName);
        proceedUrls(driver, urlsToBeCheckedSameWay);

        System.out.printf("Total likes given for %s: %s%n", providerName, givenLikes);
    }

    @Override
    public void proceedUrls(WebDriver driver, List<String> urlsToBeCheckedSameWay) {
        givenLikes = 0;
        for (String url : urlsToBeCheckedSameWay) {
            System.out.printf("Checking %s: ", url);
            runUrl(driver, url);
            try {
                Thread.sleep(TimeConstants.MILLIS_BETWEEN_URL_DELAYS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    WebElement waitForMainPageById(WebDriver driver, String htmlId) {
        // defining timeout for waiting
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TimeConstants.WEBDRIVER_TIMEOUT));

        // waiting for main page container to be visible
        return wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.id(htmlId)
                )
        );
    }

    /**
     * Perform a click on the provided like button and increment the shared counter.
     * Returns true if click likely succeeded, false on exceptions.
     */
    protected boolean performLike(WebElement likeButton) {
        if (likeButton == null) {
            return false;
        }
        try {
            likeButton.click();
            givenLikes++;
            System.out.println("Total likes given so far: " + givenLikes);
            // small human-like pause
            Utils.pretendToBeHuman(1, TimeConstants.DELAY_AFTER_LIKE_IN_SECONDS);
            return true;
        } catch (StaleElementReferenceException e) {
            System.out.println("Button went stale during click");
            return false;
        }
    }

    abstract void runUrl(WebDriver driver, String url);
}
