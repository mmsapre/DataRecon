package com.mms.data.recon.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalizes and matches free-form tags used to group datasources, domains, and profiles.
 */
public final class Tags {

    private Tags() {}

    public static List<String> normalize(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String cleaned = tag.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty()) {
                unique.add(cleaned);
            }
        }
        return List.copyOf(unique);
    }

    public static List<String> copy(Collection<String> tags) {
        return new ArrayList<>(normalize(tags));
    }

    public static boolean matches(Collection<String> tags, String requiredTag) {
        if (requiredTag == null || requiredTag.isBlank()) {
            return true;
        }
        String needle = requiredTag.trim().toLowerCase(Locale.ROOT);
        return normalize(tags).contains(needle);
    }
}
