package com.turtle.mythicweapon.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for probability/chance calculations.
 */
public class ChanceUtil {

    /**
     * Roll a chance check.
     *
     * @param percent the probability (0-100)
     * @return true if the roll succeeds
     */
    public static boolean roll(double percent) {
        if (percent <= 0) return false;
        if (percent >= 100) return true;
        return ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }
}
