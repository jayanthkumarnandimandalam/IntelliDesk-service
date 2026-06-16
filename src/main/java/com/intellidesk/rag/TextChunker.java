package com.intellidesk.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for splitting text into overlapping chunks using word-based tokenization.
 */
public class TextChunker {

    /**
     * Splits the given text into chunks of approximately {@code chunkSize} words,
     * with consecutive chunks overlapping by {@code overlap} words.
     *
     * @param text      the text to split
     * @param chunkSize maximum number of words per chunk
     * @param overlap   number of overlapping words between consecutive chunks
     * @return a list of text chunks
     * @throws IllegalArgumentException if chunkSize or overlap are invalid
     */
    public List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be at least 1");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must be non-negative");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }

        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String[] words = text.split("\\s+");

        if (words.length == 0) {
            return chunks;
        }

        // If text fits within a single chunk, return it directly
        if (words.length <= chunkSize) {
            chunks.add(text.trim());
            return chunks;
        }

        int step = chunkSize - overlap;
        int i = 0;

        while (i < words.length) {
            int end = Math.min(i + chunkSize, words.length);
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                if (j > i) {
                    sb.append(' ');
                }
                sb.append(words[j]);
            }
            chunks.add(sb.toString());

            i += step;

            // If the next chunk would start beyond the words array, stop
            if (i >= words.length) {
                break;
            }
        }

        return chunks;
    }
}
