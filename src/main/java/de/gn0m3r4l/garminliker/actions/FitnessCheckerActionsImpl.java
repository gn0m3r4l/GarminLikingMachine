package de.gn0m3r4l.garminliker.actions;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;

import de.gn0m3r4l.garminliker.utils.TimeConstants;
import de.gn0m3r4l.garminliker.utils.Utils;

public abstract class FitnessCheckerActionsImpl implements FitnessCheckActions {

    private static final Logger logger = LoggerFactory.getLogger(FitnessCheckerActionsImpl.class);
    // 8 is a good guess
    private static final int URLS_PER_PART = 8;
    protected String providerName;
    protected int givenLikes = 0;

    @Override
    public void run(Page page, List<String> urlsToBeCheckedSameWay) {

        if (urlsToBeCheckedSameWay == null || urlsToBeCheckedSameWay.isEmpty()) {
            logger.debug("No URLs provided for {}, skipping.", providerName);
            return;
        }

        logger.debug("Processing {}", providerName);
        var partedUrlList = splitUrls(urlsToBeCheckedSameWay);
        // for each left of URLs, run the checks and like activities
        for (var part : partedUrlList) {
            proceedUrls(page, part);
            // if not the last part, wait a bit before processing the next part to avoid overwhelming the system
            if (part != partedUrlList.getLast()) {
                logger.debug("Waiting some time between {}s and {}s seconds before processing " +
                                "the next batch of URLs...",
                        TimeConstants.BIGGER_PAUSE_MIN_IN_SECONDS, TimeConstants.BIGGER_PAUSE_MAX_IN_SECONDS);
                Utils.pretendToBeHuman(TimeConstants.BIGGER_PAUSE_MIN_IN_SECONDS,
                        TimeConstants.BIGGER_PAUSE_MAX_IN_SECONDS);
            }
        }

        logger.info("Total likes given for {}: {}", providerName, givenLikes);
    }

    /**
     * Splits the provided list of URLs into parts of a defined size (URLS_PER_PART) to avoid overwhelming the system.
     *
     * @param urlsToBeCheckedSameWay rawr list of urls to be checked
     * @return List of URL parts, where each part is a list of URLs to be processed together.
     */
    private List<List<String>> splitUrls(List<String> urlsToBeCheckedSameWay) {
        return urlsToBeCheckedSameWay.stream()
                .collect(Collectors.groupingBy(url -> urlsToBeCheckedSameWay.indexOf(url) / URLS_PER_PART))
                .values()
                .stream()
                .toList();
    }

    @Override
    public void proceedUrls(Page page, List<String> urlsToBeCheckedSameWay) {
        givenLikes = 0;
        for (String url : urlsToBeCheckedSameWay) {
            logger.debug("Checking {}", url);
            runUrl(page, url);
            try {
                Thread.sleep(TimeConstants.MILLIS_BETWEEN_URL_DELAYS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected Locator waitForMainPageById(Page page, String htmlId) {
        Locator locator = page.locator("#" + htmlId);
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(TimeConstants.BROWSER_TIMEOUT * TimeConstants.MILLIS_TO_SECONDS_FACTOR));
        return locator;
    }

    /**
     * Perform a click on the provided like button and increment the shared counter.
     *
     * @param likeButton locator to be clicked
     * @return true if click likely succeeded, false on exceptions
     */
    protected boolean performLike(Locator likeButton) {
        if (likeButton == null) {
            return false;
        }
        try {
            likeButton.click();
            givenLikes++;
            logger.info("Total likes given so far: {}", givenLikes);
            // small human-like pause
            Utils.pretendToBeHuman(1, TimeConstants.DELAY_AFTER_LIKE_IN_SECONDS);
            return true;
        } catch (PlaywrightException e) {
            logger.debug("Button interaction failed during click");
            return false;
        }
    }

    protected abstract void runUrl(Page page, String url);
}
