package de.gn0m3r4l.garminliker.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static de.gn0m3r4l.garminliker.utils.TimeConstants.LOG_INTERVAL_SECONDS;

public class AliveLogger implements Runnable {

    private boolean running = true;
    private static final Logger logger = LoggerFactory.getLogger(AliveLogger.class);

    @Override
    public void run() {
        long startingMillies = System.currentTimeMillis();
        while (running) {
            try {
                Thread.sleep(LOG_INTERVAL_SECONDS * TimeConstants.MILLIS_TO_SECONDS_FACTOR);
                var deltaMillies = System.currentTimeMillis() - startingMillies;
                logger.info("Still alive. Job running for {} seconds.  If nothing happens, " +
                                "check for planned pause notification in Logs."
                        , (int) (deltaMillies / TimeConstants.MILLIS_TO_SECONDS_FACTOR));
            } catch (InterruptedException e) { }
        }
    }

    /**
     * Changes the running value. Most likely when everything is finished
     * @param running
     */
    public void setRunning(boolean running) {
        this.running = running;
    }
}
