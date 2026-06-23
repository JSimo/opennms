/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.filter.ast;

import java.util.ArrayList;
import java.util.List;

import org.opennms.netmgt.filter.api.FilterParseException;

/**
 * Tokenizes a raw filter rule string into a list of {@link Token}s.
 * Performs quote extraction (single- and double-quoted strings become PLACEHOLDER tokens; values
 * are stored in {@link #getExtractedStrings()}), recognizes unquoted IPLIKE pattern values, and
 * emits AND/OR/NOT/EQ/NE for operator aliases (&, &&, |, ||, !, =, ==, !=).
 */
class FilterTokenizer {

    private final String input;
    private int pos;
    private final List<String> extractedStrings = new ArrayList<>();
    /** When we read "IPLIKE" followed by unquoted pattern, we emit IPLIKE then STRING; STRING is queued here. */
    private final List<Token> pendingTokens = new ArrayList<>();

    public FilterTokenizer(String rawRule) {
        this.input = rawRule != null ? rawRule : "";
        this.pos = 0;
    }

    /**
     * Strings extracted from quoted literals in the rule (single- or double-quoted), in order.
     * Populated during {@link #tokenize()}. Stored in SQL-ready form (single-quoted, '' escaped).
     *
     * @return list of extracted strings (never null; empty before tokenize())
     */
    public List<String> getExtractedStrings() {
        return extractedStrings;
    }

    /**
     * Produce a list of tokens for the entire input, ending with EOF.
     * Populates {@link #getExtractedStrings()} when quoted strings are encountered.
     *
     * @return list of tokens (never null)
     * @throws FilterParseException if an unexpected character or unmatched quote is encountered
     */
    public List<Token> tokenize() throws FilterParseException {
        extractedStrings.clear();
        pendingTokens.clear();
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length()) {
                break;
            }
            Token t = nextToken();
            if (t.getType() == TokenType.EOF) {
                break;
            }
            tokens.add(t);
        }
        // Drain pending tokens (e.g. STRING after "IPLIKE" when unquoted pattern was read)
        while (!pendingTokens.isEmpty()) {
            tokens.add(pendingTokens.remove(0));
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private Token nextToken() throws FilterParseException {
        if (!pendingTokens.isEmpty()) {
            return pendingTokens.remove(0);
        }
        if (pos >= input.length()) {
            return new Token(TokenType.EOF, "");
        }
        char c = input.charAt(pos);

        if (c == '(') {
            pos++;
            return new Token(TokenType.LPAREN, "(");
        }
        if (c == ')') {
            pos++;
            return new Token(TokenType.RPAREN, ")");
        }
        if (c == '=') {
            pos++;
            if (pos < input.length() && input.charAt(pos) == '=') {
                pos++;
            }
            return new Token(TokenType.EQ, "=");
        }
        if (c == '!') {
            pos++;
            if (pos < input.length() && input.charAt(pos) == '=') {
                pos++;
                return new Token(TokenType.NE, "!=");
            }
            return new Token(TokenType.NOT, "NOT");
        }
        if (c == '&') {
            pos++;
            if (pos < input.length() && input.charAt(pos) == '&') {
                pos++;
            }
            return new Token(TokenType.AND, "AND");
        }
        if (c == '|') {
            pos++;
            if (pos < input.length() && input.charAt(pos) == '|') {
                pos++;
            }
            return new Token(TokenType.OR, "OR");
        }
        if (c == ',') {
            pos++;
            return new Token(TokenType.COMMA, ",");
        }
        if (c == ':') {
            pos++;
            if (pos < input.length() && input.charAt(pos) == ':') {
                pos++;
                return new Token(TokenType.DOUBLE_COLON, "::");
            }
            throw new FilterParseException("Unexpected ':' at position " + (pos - 1));
        }
        if (c == '\'') {
            return readSingleQuotedString();
        }
        if (c == '"') {
            return readDoubleQuotedString();
        }

        if (isIdentifierStart(c)) {
            return readIdentifierOrKeyword();
        }

        throw new FilterParseException("Unexpected character at position " + pos + ": '" + c + "'");
    }

    /**
     * Read a single-quoted string, store SQL form in extractedStrings, return PLACEHOLDER(index).
     */
    private Token readSingleQuotedString() throws FilterParseException {
        int start = pos;
        pos++; // consume opening '
        StringBuilder content = new StringBuilder();
        boolean foundClosing = false;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\'') {
                pos++;
                if (pos < input.length() && input.charAt(pos) == '\'') {
                    content.append('\'');
                    pos++;
                } else {
                    foundClosing = true;
                    break;
                }
            } else {
                content.append(c);
                pos++;
            }
        }
        if (!foundClosing) {
            throw new FilterParseException("Unmatched ' in filter rule at position " + start);
        }
        String stored = "'" + content.toString().replace("'", "''") + "'";
        extractedStrings.add(stored);
        String index = String.valueOf(extractedStrings.size() - 1);
        return new Token(TokenType.PLACEHOLDER, index);
    }

    /**
     * Read a double-quoted string, convert to single-quoted SQL form, store in extractedStrings, return PLACEHOLDER(index).
     */
    private Token readDoubleQuotedString() throws FilterParseException {
        int start = pos;
        pos++; // consume opening "
        StringBuilder content = new StringBuilder();
        boolean foundClosing = false;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '"') {
                pos++;
                if (pos < input.length() && input.charAt(pos) == '"') {
                    content.append('"');
                    pos++;
                } else {
                    foundClosing = true;
                    break;
                }
            } else {
                content.append(c);
                pos++;
            }
        }
        if (!foundClosing) {
            throw new FilterParseException("Unmatched \" in filter rule at position " + start);
        }
        String stored = "'" + content.toString().replace("'", "''") + "'";
        extractedStrings.add(stored);
        String index = String.valueOf(extractedStrings.size() - 1);
        return new Token(TokenType.PLACEHOLDER, index);
    }

    private Token readIdentifierOrKeyword() throws FilterParseException {
        int start = pos;
        while (pos < input.length() && isIdentifierPart(input.charAt(pos))) {
            pos++;
        }
        String lex = input.substring(start, pos);
        if ("IPLIKE".equalsIgnoreCase(lex)) {
            skipWhitespace();
            if (pos < input.length() && isIplikePatternChar(input.charAt(pos))) {
                String pattern = readIplikePattern();
                pendingTokens.add(new Token(TokenType.STRING, pattern));
                return new Token(TokenType.IDENTIFIER, "IPLIKE");
            }
        }
        TokenType kw = keyword(lex);
        return kw != null ? new Token(kw, lex) : new Token(TokenType.IDENTIFIER, lex);
    }

    private static boolean isIplikePatternChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || c == '.' || c == ':' || c == '*' || c == ',' || c == '-';
    }

    private String readIplikePattern() {
        int start = pos;
        while (pos < input.length() && isIplikePatternChar(input.charAt(pos))) {
            pos++;
        }
        return input.substring(start, pos);
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '-';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static TokenType keyword(String lex) {
        if (lex == null || lex.isEmpty()) {
            return null;
        }
        switch (lex.toUpperCase()) {
            case "AND":
                return TokenType.AND;
            case "OR":
                return TokenType.OR;
            case "NOT":
                return TokenType.NOT;
            default:
                return null;
        }
    }
}
