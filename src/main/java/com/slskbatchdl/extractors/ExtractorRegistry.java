package com.slskbatchdl.extractors;

import com.slskbatchdl.enums.InputType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ExtractorRegistry {
    private static final List<ExtractorEntry> extractors = new ArrayList<>();

    static {
        extractors.add(new ExtractorEntry(InputType.CSV, CsvExtractor::inputMatches, CsvExtractor::new));
        extractors.add(new ExtractorEntry(InputType.YouTube, YouTubeExtractor::inputMatches, YouTubeExtractor::new));
        extractors.add(new ExtractorEntry(InputType.Spotify, SpotifyExtractor::inputMatches, SpotifyExtractor::new));
        extractors.add(new ExtractorEntry(InputType.Bandcamp, BandcampExtractor::inputMatches, BandcampExtractor::new));
        extractors.add(new ExtractorEntry(InputType.String, StringExtractor::inputMatches, StringExtractor::new));
        extractors.add(new ExtractorEntry(InputType.List, ListExtractor::inputMatches, ListExtractor::new));
    }

    public static ExtractorEntry getMatchingExtractor(String input, InputType inputType) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be null or empty.");
        }

        if (inputType != InputType.None) {
            return extractors.stream()
                    .filter(entry -> entry.getType() == inputType)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No matching extractor for input type '" + inputType + "'"));
        }

        return extractors.stream()
                .filter(entry -> entry.getInputMatches().test(input))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matching extractor for input '" + input + "'"));
    }

    public static class ExtractorEntry {
        private final InputType type;
        private final Predicate<String> inputMatches;
        private final Function<Void, IExtractor> extractor;

        public ExtractorEntry(InputType type, Predicate<String> inputMatches, Function<Void, IExtractor> extractor) {
            this.type = type;
            this.inputMatches = inputMatches;
            this.extractor = extractor;
        }

        public InputType getType() {
            return type;
        }

        public Predicate<String> getInputMatches() {
            return inputMatches;
        }

        public IExtractor createExtractor() {
            return extractor.apply(null);
        }
    }
}
