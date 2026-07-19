package com.examcore.util;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfanityFilter {

    private static final List<String> BANNED_WORDS = List.of(
            "damn", "hell", "crap", "ass", "asshole", "bastard", "bitch",
            "bullshit", "shit", "fuck", "fucking", "fucker", "dick",
            "piss", "slut", "whore", "retard", "cunt"
    );

    private static final Pattern PATTERN = Pattern.compile(
            "\\b(" + String.join("|", BANNED_WORDS) + ")\\b", Pattern.CASE_INSENSITIVE);

    private ProfanityFilter() {
    }

    public static boolean containsProfanity(String text) {
        return findBannedWord(text).isPresent();
    }

    public static Optional<String> findBannedWord(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }
}
