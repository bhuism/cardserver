package nl.appsource.cardserver.utils;

import java.security.SecureRandom;
import java.util.Random;
import java.util.Set;

public final class Utils {

    private static final Random RAND = new SecureRandom();

    private static final Set<String> ADMINS = Set.of("YqI3QttWmwRKgyTIX9Idok1admqp", "BbdQWhNJjyfawQhWwWDv1aXncQu2");

    public static boolean isAdmin(final String userId) {
        return ADMINS.contains(userId);
    }

    public static String idGen(final IDTYPE type, final int length) {
        final String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final StringBuilder result = new StringBuilder(length + 4);

        if (length < 8) {
            throw new IllegalArgumentException();
        }

        result.append(type.getIdentifier());

        for (int i = 0; i < length; i++) {
            int index = RAND.nextInt(characters.length());
            result.append(characters.charAt(index));
        }

        return result.toString();
    }

    public static final Set<String> AI_USER_ID = Set.of("2ab5fd69a2796c4740380cd98eb7", "2ab5fd69a2796c4740380cd98eb8", "2ab5fd69a2796c4740380cd98eb9", "2ab5fd69a2796c4740380cd98eba");

    public static boolean isAiPlayer(final String userId) {
        return AI_USER_ID.contains(userId);
    }


}
