package com.examcore.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfanityFilter {

    private static final String RESOURCE_PATH = "/banned-words.txt";
    private static final List<String> BANNED_WORDS = loadBannedWords();
    private static final Pattern PATTERN = Pattern.compile(
            "\\b(" + String.join("|", BANNED_WORDS) + ")\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ProfanityFilter() {}

    private static List<String> loadBannedWords() {
        try (InputStream in = ProfanityFilter.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + RESOURCE_PATH);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .map(ProfanityFilter::normalize)
                        .toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String normalize(String text) {
        String result = text.toLowerCase(Locale.forLanguageTag("tr"));

        result = result.replace("sh", "s").replace("ch", "c");  

        result = result
                .replace('ş', 's').replace('ç', 'c')
                .replace('ğ', 'g').replace('ı', 'i')
                .replace('ö', 'o').replace('ü', 'u');

        result = result
                .replace('0', 'o').replace('1', 'i')
                .replace('3', 'e').replace('4', 'a')
                .replace('5', 's').replace('7', 't')
                .replace('@', 'a').replace('$', 's');

        return result;
    }

    public static boolean containsProfanity(String text) {
        return findBannedWord(text).isPresent();
    }

    public static Optional<String> findBannedWord(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        Matcher matcher = PATTERN.matcher(normalized);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}