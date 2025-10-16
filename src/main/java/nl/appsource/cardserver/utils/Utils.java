package nl.appsource.cardserver.utils;

import java.util.Set;

public final class Utils {

    private static final Set<String> ADMINS = Set.of("YqI3QttWmwRKgyTIX9Idok1admqp", "BbdQWhNJjyfawQhWwWDv1aXncQu2");

    public static boolean isAdmin(final String userId) {
        return ADMINS.contains(userId);
    }
}
