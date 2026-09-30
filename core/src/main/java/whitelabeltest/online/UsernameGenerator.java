package whitelabeltest.online;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Leaderboard names are one word from each list joined together, e.g. "SwiftFalcon" (1024
 *  combinations). Players can only pick generated names, so no free text ever reaches the
 *  leaderboard. The server must validate names with isValid() against these same lists. */
public final class UsernameGenerator {
    public static final String[] FIRST_WORDS = {
        "Amber", "Arctic", "Azure", "Bold", "Brave", "Bright", "Calm", "Clever",
        "Cobalt", "Cosmic", "Crimson", "Crystal", "Daring", "Electric", "Emerald", "Frosty",
        "Gentle", "Golden", "Hidden", "Jade", "Lucky", "Lunar", "Mighty", "Neon",
        "Nimble", "Quiet", "Rapid", "Silver", "Solar", "Swift", "Velvet", "Violet"
    };

    public static final String[] SECOND_WORDS = {
        "Beacon", "Cipher", "Circuit", "Comet", "Dragon", "Falcon", "Fox", "Glacier",
        "Golem", "Hawk", "Heron", "Knight", "Koala", "Lynx", "Mantis", "Meteor",
        "Nebula", "Orbit", "Otter", "Owl", "Panda", "Phoenix", "Pilot", "Pixel",
        "Raven", "Rocket", "Spark", "Sparrow", "Tiger", "Voyager", "Wizard", "Wolf"
    };

    private UsernameGenerator() {}

    public static String generate(Random random) {
        return FIRST_WORDS[random.nextInt(FIRST_WORDS.length)] + SECOND_WORDS[random.nextInt(SECOND_WORDS.length)];
    }

    /** `count` distinct names, none of them in `exclude`. */
    public static List<String> candidates(int count, Set<String> exclude, Random random) {
        Set<String> names = new LinkedHashSet<>();
        int maxNames = FIRST_WORDS.length * SECOND_WORDS.length - (exclude != null ? exclude.size() : 0);
        while (names.size() < Math.min(count, maxNames)) {
            String name = generate(random);
            if (exclude == null || !exclude.contains(name)) names.add(name);
        }
        return new ArrayList<>(names);
    }

    /** True only for exactly one first word followed by exactly one second word (case-sensitive). */
    public static boolean isValid(String name) {
        if (name == null) return false;
        for (String first : FIRST_WORDS) {
            if (!name.startsWith(first)) continue;
            String rest = name.substring(first.length());
            for (String second : SECOND_WORDS) {
                if (rest.equals(second)) return true;
            }
        }
        return false;
    }
}
