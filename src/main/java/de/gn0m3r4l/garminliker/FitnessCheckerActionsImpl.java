package de.gn0m3r4l.garminliker;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public abstract class FitnessCheckerActionsImpl implements FitnessCheckActions{

    protected String providerName;
    protected int givenLikes = 0;

    // safe getter: if subclass did not set providerName, fallback to class simple name
    protected String getProviderName() {
        return providerName != null ? providerName : this.getClass().getSimpleName();
    }

    @Override
    public void run(WebDriver driver, List<String> urlsToBeCheckedSameWay) {

        if (urlsToBeCheckedSameWay == null || urlsToBeCheckedSameWay.isEmpty()) {
            System.out.printf("No URLs provided for %s, skipping.%n", getProviderName());
            return;
        }

        System.out.printf("Processing %s%n", getProviderName());
        proceedUrls(driver, urlsToBeCheckedSameWay);


        System.out.printf("Total likes given for %s: %s", getProviderName(), givenLikes);
    }

    @Override
    public void proceedUrls(WebDriver driver, List<String> urlsToBeCheckedSameWay) {
        givenLikes = 0;
        for (String url : urlsToBeCheckedSameWay) {
            System.out.printf("Checking %s url: ", url);
            runUrl(driver, url);
            try {
                Thread.sleep(3500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    WebElement waitForMainPageById(WebDriver driver, String htmlId) {
        // defining timeout for waiting
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        // waiting for main page container to be visible
        return wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.id(htmlId)
                )
        );
    }

    abstract void runUrl(WebDriver driver, String url);


}
