package de.gn0m3r4l.garminliker.actions;

import java.util.List;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;

public interface FitnessCheckActions {
    /**
     * Run the fitness check for the given driver and list of urls. The implementation should call proceedUrls
     * with the same parameters.
     *
     * @param page                   Page instance to run checks
     * @param urlsToBeCheckedSameWay List of urls to be checked in the same way. The implementation should
     *                               call proceedUrls with the same parameters.
     */
    void run(Page page, List<String> urlsToBeCheckedSameWay) throws TimeoutError;

    /**
     * Checking the given urls after custom checks
     *
     * @param page                   Page instance to run checks
     * @param urlsToBeCheckedSameWay List of urls to be checked in the same way.
     */
    void proceedUrls(Page page, List<String> urlsToBeCheckedSameWay);

    /**
     * @return name of the provider for logging purposes
     */
    String getProviderName();

    /**
     * @return likes given during the last call of run
     */
    int getGivenLikes();

    /**
     * @return true if the max likes per run for this provider are reached
     */
    boolean likeLimitReached();
}
