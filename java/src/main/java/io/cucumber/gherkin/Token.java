package io.cucumber.gherkin;

import io.cucumber.messages.types.Location;
import io.cucumber.messages.types.StepKeywordType;

import java.util.List;

import static java.util.Objects.requireNonNull;

class Token {
    final GherkinLine line;
    final boolean eof;
    Parser.TokenType matchedType;
    String matchedKeyword;
    String matchedText;
    List<GherkinLineSpan> matchedItems;
    int matchedIndent;
    GherkinDialect matchedGherkinDialect;
    StepKeywordType keywordType;
    Location location;

    private Token(GherkinLine line, Location location) {
        this.line = line;
        this.location = location;
        this.eof = line == null;
    }

    static Token createEOF(Location location) {
        requireNonNull(location);
        return new Token(null, location);
    }

    static Token createGherkinLine(String text, Location location) {
        requireNonNull(text);
        requireNonNull(location);
        return new Token(new GherkinLine(text, location), location);
    }

    boolean isEOF() {
        return eof;
    }

    String getTokenValue() {
        return isEOF() ? "EOF" : line.getText();
    }

    @Override
    public String toString() {
        return String.format("%s: %s/%s", matchedType, matchedKeyword, matchedText);
    }
}
