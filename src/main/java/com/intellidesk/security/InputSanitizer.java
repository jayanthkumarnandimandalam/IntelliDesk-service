package com.intellidesk.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects and neutralizes prompt injection patterns in user input.
 * <p>
 * Strips injection patterns while preserving legitimate query content.
 * If the entire input consists of injection patterns (nothing legitimate remains),
 * it is classified as malicious and should be rejected with HTTP 400.
 * </p>
 *
 * @see <a href="requirements.md">Requirements 14.3, 14.4</a>
 */
@Component
public class InputSanitizer {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // Role override patterns
            Pattern.compile("(?i)\\bignore\\s+(all\\s+)?previous\\s+instructions\\b"),
            Pattern.compile("(?i)\\bignore\\s+all\\s+instructions\\b"),
            Pattern.compile("(?i)\\byou\\s+are\\s+now\\b"),
            Pattern.compile("(?i)\\bact\\s+as\\b"),
            Pattern.compile("(?i)\\bpretend\\s+(you\\s+are|to\\s+be)\\b"),
            Pattern.compile("(?i)\\bfrom\\s+now\\s+on\\s+you\\s+are\\b"),
            Pattern.compile("(?i)\\byour\\s+new\\s+role\\s+is\\b"),

            // System prompt markers
            Pattern.compile("(?i)\\bsystem\\s*:"),
            Pattern.compile("(?i)###\\s*System"),
            Pattern.compile("(?i)\\[INST\\]"),
            Pattern.compile("(?i)\\[/INST\\]"),
            Pattern.compile("(?i)<<\\s*SYS\\s*>>"),
            Pattern.compile("(?i)<\\|im_start\\|>"),
            Pattern.compile("(?i)<\\|im_end\\|>"),

            // Prompt extraction attempts
            Pattern.compile("(?i)\\brepeat\\s+the\\s+above\\b"),
            Pattern.compile("(?i)\\bshow\\s+me\\s+your\\s+prompt\\b"),
            Pattern.compile("(?i)\\bprint\\s+your\\s+(system\\s+)?instructions\\b"),
            Pattern.compile("(?i)\\bwhat\\s+are\\s+your\\s+instructions\\b"),
            Pattern.compile("(?i)\\bwhat\\s+is\\s+your\\s+system\\s+prompt\\b"),
            Pattern.compile("(?i)\\boutput\\s+your\\s+initial\\s+prompt\\b"),
            Pattern.compile("(?i)\\bshow\\s+me\\s+your\\s+system\\s+message\\b"),

            // Delimiter injection attempts
            Pattern.compile("```[\\s\\S]*?```"),
            Pattern.compile("(?i)---+\\s*new\\s+conversation\\s*---+"),
            Pattern.compile("(?i)\\b(end|begin)\\s+of\\s+(system|user|assistant)\\s+(message|prompt)\\b")
    );

    /**
     * Sanitizes the input by stripping detected prompt injection patterns
     * while preserving legitimate query content.
     *
     * @param input the raw user input
     * @return sanitized string with injection patterns removed, or empty string if input is null
     */
    public String sanitize(String input) {
        if (input == null) {
            return "";
        }

        String result = input;
        for (Pattern pattern : INJECTION_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }

        // Collapse multiple spaces left by pattern removal into single space
        result = result.replaceAll("\\s{2,}", " ").trim();

        return result;
    }

    /**
     * Determines whether the input is entirely a prompt injection attempt
     * with no legitimate content remaining after sanitization.
     *
     * @param input the raw user input
     * @return true if the input is entirely malicious (nothing useful remains after stripping)
     */
    public boolean isMalicious(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String sanitized = sanitize(input);
        return sanitized.isBlank();
    }
}
