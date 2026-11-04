package de.example.riftkingextras;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces strong swear words in lyrics with stars (option "Profanity filter"). Whole words only. */
final class ProfanityFilter {
    private static final String[] WORDS = {
            "fuck", "fucking", "fucker", "fucked", "motherfucker", "shit", "shitty", "bullshit", "bitch", "bitches",
            "asshole", "dick", "cunt", "cock", "pussy", "bastard", "whore", "slut", "fag", "faggot",
            "nigga", "niggas", "nigger"};

    private static final Pattern PATTERN;

    static {
        StringBuilder sb = new StringBuilder("\\b(");
        for (int i = 0; i < WORDS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(WORDS[i]));
        }
        sb.append(")\\b");
        PATTERN = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private ProfanityFilter() {}

    static String apply(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = PATTERN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String word = m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(
                    word.charAt(0) + "*".repeat(Math.max(0, word.length() - 1))));
        }
        m.appendTail(out);
        return out.toString();
    }
}
