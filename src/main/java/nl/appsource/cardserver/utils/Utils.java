package nl.appsource.cardserver.utils;

import java.util.Set;

public final class Utils {

    private static final Set<String> ADMINS = Set.of("BbdQWhNJjyfawQhWwWDv1aXncQu2", "J1Z2dqmV5Q68ikGLOvk1t8yIVoWX", "GwsuFaXvG4dEvVIUhbpwLuQ4DYu1");

    public static boolean isAdmin(final String userId) {
        return ADMINS.contains(userId);
    }
}
