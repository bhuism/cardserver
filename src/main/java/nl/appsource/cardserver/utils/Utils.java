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

        if (length < 16) {
            throw new IllegalArgumentException();
        }

        result.append(type.getIdentifier());

        for (int i = 0; i < length; i++) {
            int index = RAND.nextInt(characters.length());
            result.append(characters.charAt(index));
        }

        return result.toString();
    }

}
