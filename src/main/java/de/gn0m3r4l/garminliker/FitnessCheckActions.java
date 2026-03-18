package de.gn0m3r4l.garminliker;

import java.util.List;

import org.openqa.selenium.WebDriver;

public interface FitnessCheckActions {
    void run(WebDriver driver, List<String> urlsToBeCheckedSameWay);
    void proceedUrls(WebDriver driver, List<String> urlsToBeCheckedSameWay);
}
