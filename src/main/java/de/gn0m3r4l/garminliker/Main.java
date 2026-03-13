package de.gn0m3r4l.garminliker;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {


    public static void main(String[] args) {
        var providerMap = splitArgs(args);

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "localhost:9222");
        WebDriver driver = new ChromeDriver(options);

        StravaChecker.getInstance().run(driver, providerMap.get("strava"));
        GarminChecker.getInstance().run(driver, providerMap.get("garmin"));
        //2nd run for strava as it feels that it shows different stuff each time
        StravaChecker.getInstance().run(driver, providerMap.get("strava"));

        driver.quit();
        System.exit(0);
    }


    private static Map<String, List<String>> splitArgs(String[] args) {
        Map<String, List<String>> providerMap = new HashMap<>();
        for (var arg : args) {
            var split = arg.split("=", 2);
            if (split.length == 2) {
                var provider = split[0].trim().toLowerCase();
                var url = split[1].trim();
                providerMap.computeIfAbsent(provider, k -> new java.util.ArrayList<>()).add(url);
            } else {
                System.out.println("Invalid argument format: " + arg + ". Expected format: provider=url");
            }
        }
        return providerMap;
    }

}