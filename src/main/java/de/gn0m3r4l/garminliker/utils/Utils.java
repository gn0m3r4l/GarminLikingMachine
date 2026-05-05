package de.gn0m3r4l.garminliker.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class containing helper methods for the Browser application.
 */
public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
    /**
     * Simple method to mimic human behavior by sleeping a random time
     * between minSeconds and maxSeconds.
     * Application crashes when sleep is interrupted.
     *
     * @param minSeconds Minimum seconds to sleep
     * @param maxSeconds Maximum seconds to sleep
     */
    public static void pretendToBeHuman(int minSeconds, int maxSeconds) {
        int sleepTime = (int) ((Math.random() * (maxSeconds - minSeconds) + minSeconds)
                * TimeConstants.MILLIS_TO_SECONDS_FACTOR);
        logger.debug("Sleeping for {} milliseconds to mimic human behavior.", sleepTime);
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            logger.error("If this goes wrong, blame the interrupt.");
            System.exit(1);
        }
    }

    /**
     * Overloaded method to mimic human behavior by sleeping a random time between
     * seconds and seconds + 1.
     *
     * @param seconds Delay in seconds to sleep
     */
    public static void pretendToBeHuman(int seconds) {
        pretendToBeHuman(seconds, seconds);
    }
}
