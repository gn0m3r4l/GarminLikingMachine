package de.gn0m3r4l.garminliker;

import java.util.List;

import org.openqa.selenium.WebDriver;

public interface FitnessCheckActions {
    /**
     * Run the fitness check for the given driver and list of urls. The implementation should call proceedUrls
     * with the same parameters.
     * @param driver Webdriver instance to run checks
     * @param urlsToBeCheckedSameWay List of urls to be checked in the same way. The implementation should
     *                               call proceedUrls with the same parameters.
     */
    void run(WebDriver driver, List<String> urlsToBeCheckedSameWay);

    /**
     * Checking the given urls after custom checks
     * @param driver Webdriver instance to run checks
     * @param urlsToBeCheckedSameWay List of urls to be checked in the same way.
     */
    void proceedUrls(WebDriver driver, List<String> urlsToBeCheckedSameWay);
}
