package de.gn0m3r4l.garminliker;

import org.openqa.selenium.WebDriver;

import java.util.List;

public interface FitnessCheckActions {
    void run(WebDriver driver, List<String> urlsToBeCheckedSameWay);
    void proceedUrls(WebDriver driver, List<String> urlsToBeCheckedSameWay);
}
