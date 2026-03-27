package de.gn0m3r4l.garminliker.utils;

/**
 * Utility class containing helper methods for the Browser application.
 */
public class Utils {
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
        System.out.println("Sleeping for " + sleepTime + " milliseconds to mimic human behavior.");
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            System.err.println("If this goes wrong, blame the interrupt.");
            System.exit(1);
        }
    }

    /**
     * Overloaded method to mimic human behavior by sleeping a random time between
     * seconds and seconds + 1.
     * @param seconds Delay in seconds to sleep
     */
    public static void pretendToBeHuman(int seconds) {
        pretendToBeHuman(seconds, seconds);
    }
}
