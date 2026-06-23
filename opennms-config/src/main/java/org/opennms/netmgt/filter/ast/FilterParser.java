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
 * Recursive-descent parser for filter rules.
 * Consumes a list of tokens (from {@link FilterTokenizer}) and produces an {@link Expr} tree.
 * <p>
 * Grammar (precedence low to high): orExpr → andExpr (OR andExpr)* ; andExpr → unaryExpr (AND unaryExpr)* ;
 * unaryExpr → NOT unaryExpr | comparison ; comparison → primary (EQ primary)? ; primary → LPAREN expr RPAREN |
 * catincId | isId | notisId | identifier | PLACEHOLDER.
 */
class FilterParser {

    private final List<Token> tokens;
    private int index;

    public FilterParser(List<Token> tokens) {
        this.tokens = new ArrayList<>(tokens != null ? tokens : List.of());
        this.index = 0;
    }

    /**
     * Parse the token list into a single expression, or null if input is empty / only EOF.
     *
     * @return root expression, or null
     * @throws FilterParseException on syntax error or unexpected token
     */
    public Expr parse() throws FilterParseException {
        if (tokens.isEmpty() || peek().getType() == TokenType.EOF) {
            return null;
        }
        Expr e = parseOrExpr();
        if (e != null && peek().getType() != TokenType.EOF) {
            throw new FilterParseException("Unexpected token at end: " + peek());
        }
        return e;
    }

    private Expr parseOrExpr() throws FilterParseException {
        List<Expr> terms = new ArrayList<>();
        terms.add(parseAndExpr());
        while (match(TokenType.OR)) {
            terms.add(parseAndExpr());
        }
        return terms.size() == 1 ? terms.get(0) : new OrExpr(terms);
    }

    private Expr parseAndExpr() throws FilterParseException {
        List<Expr> terms = new ArrayList<>();
        terms.add(parseUnaryExpr());
        while (match(TokenType.AND)) {
            terms.add(parseUnaryExpr());
        }
        return terms.size() == 1 ? terms.get(0) : new AndExpr(terms);
    }

    private Expr parseUnaryExpr() throws FilterParseException {
        if (match(TokenType.NOT)) {
            Expr child = parseUnaryExpr();
            return new NotExpr(child);
        }
        return parseComparison();
    }

    private Expr parseComparison() throws FilterParseException {
        Expr left = parsePrimary();
        if (peek().getType() == TokenType.EQ) {
            advance();
            Expr right = parsePrimary();
            return new ComparisonExpr(left, "=", right);
        }
        if (peek().getType() == TokenType.NE) {
            advance();
            Expr right = parsePrimary();
            return new ComparisonExpr(left, "!=", right);
        }
        // "primary IPLIKE primary" (operator form), e.g. ipAddr IPLIKE '10.0.0.*' or ipAddr IPLIKE ###@0@###
        if (peek().getType() == TokenType.IDENTIFIER && "IPLIKE".equalsIgnoreCase(peek().getValue())) {
            advance();
            Expr right = parsePrimary();
            return new IplikeExpr(left, right);
        }
        // "primary LIKE primary" (SQL LIKE), e.g. nodeSysOID LIKE '.1.3.6.1.4.1.6527.1.3.%'
        if (peek().getType() == TokenType.IDENTIFIER && "LIKE".equalsIgnoreCase(peek().getValue())) {
            advance();
            Expr right = parsePrimary();
            return new ComparisonExpr(left, "LIKE", right);
        }
        // "primary NOT LIKE primary" (SQL NOT LIKE); NOT is token type NOT (keyword) or identifier
        boolean isNot = peek().getType() == TokenType.NOT
                || (peek().getType() == TokenType.IDENTIFIER && "NOT".equalsIgnoreCase(peek().getValue()));
        if (isNot && peekNext().getType() == TokenType.IDENTIFIER && "LIKE".equalsIgnoreCase(peekNext().getValue())) {
            advance(); // NOT
            advance(); // LIKE
            Expr right = parsePrimary();
            return new ComparisonExpr(left, "NOT LIKE", right);
        }
        // "primary NOT IN ( primary , ... )" (SQL NOT IN)
        boolean notBeforeIn = peek().getType() == TokenType.NOT
                || (peek().getType() == TokenType.IDENTIFIER && "NOT".equalsIgnoreCase(peek().getValue()));
        if (notBeforeIn && peekNext().getType() == TokenType.IDENTIFIER
                && "IN".equalsIgnoreCase(peekNext().getValue())) {
            advance(); // NOT
            advance(); // IN
            if (!match(TokenType.LPAREN)) {
                throw new FilterParseException("Expected '(' after NOT IN at " + peek());
            }
            List<Expr> values = new ArrayList<>();
            values.add(parsePrimary());
            while (match(TokenType.COMMA)) {
                values.add(parsePrimary());
            }
            if (!match(TokenType.RPAREN)) {
                throw new FilterParseException("Expected ')' after NOT IN list at " + peek());
            }
            return new InExpr(left, values, true);
        }
        // "primary IN ( primary , primary , ... )" (SQL IN)
        if (peek().getType() == TokenType.IDENTIFIER && "IN".equalsIgnoreCase(peek().getValue())) {
            advance(); // IN
            if (!match(TokenType.LPAREN)) {
                throw new FilterParseException("Expected '(' after IN at " + peek());
            }
            List<Expr> values = new ArrayList<>();
            values.add(parsePrimary());
            while (match(TokenType.COMMA)) {
                values.add(parsePrimary());
            }
            if (!match(TokenType.RPAREN)) {
                throw new FilterParseException("Expected ')' after IN list at " + peek());
            }
            return new InExpr(left, values);
        }
        // "primary IS NULL" / "primary IS NOT NULL" / "primary IS [NOT] DISTINCT FROM primary"
        if (peek().getType() == TokenType.IDENTIFIER && "IS".equalsIgnoreCase(peek().getValue())) {
            advance(); // IS
            boolean notDistinct = peek().getType() == TokenType.NOT
                    || (peek().getType() == TokenType.IDENTIFIER && "NOT".equalsIgnoreCase(peek().getValue()));
            if (notDistinct) {
                advance(); // NOT
            }
            if (peek().getType() == TokenType.IDENTIFIER && "DISTINCT".equalsIgnoreCase(peek().getValue())) {
                advance(); // DISTINCT
                if (peek().getType() != TokenType.IDENTIFIER || !"FROM".equalsIgnoreCase(peek().getValue())) {
                    throw new FilterParseException("Expected FROM after IS DISTINCT at " + peek());
                }
                advance(); // FROM
                Expr right = parsePrimary();
                String op = notDistinct ? "IS NOT DISTINCT FROM" : "IS DISTINCT FROM";
                return new ComparisonExpr(left, op, right);
            }
            if (notDistinct) {
                if (peek().getType() != TokenType.IDENTIFIER || !"NULL".equalsIgnoreCase(peek().getValue())) {
                    throw new FilterParseException("Expected NULL after IS NOT at " + peek());
                }
                advance(); // NULL
                return new IsNullExpr(left, true);
            }
            if (peek().getType() == TokenType.IDENTIFIER && "NULL".equalsIgnoreCase(peek().getValue())) {
                advance(); // NULL
                return new IsNullExpr(left, false);
            }
            throw new FilterParseException("Expected NULL, NOT NULL, or DISTINCT FROM after IS at " + peek());
        }
        return left;
    }

    private Expr parseColumnRef(String id) throws FilterParseException {
        String typeCast = null;
        if (match(TokenType.DOUBLE_COLON)) {
            if (peek().getType() != TokenType.IDENTIFIER) {
                throw new FilterParseException("Expected type name after :: at " + peek());
            }
            String castName = advance().getValue().toUpperCase();
            if (!"TIMESTAMP".equals(castName) && !"INET".equals(castName)) {
                throw new FilterParseException("Unsupported type cast ::" + castName);
            }
            typeCast = castName;
        }
        return new ColumnRefExpr(id, typeCast);
    }

    private Expr parsePrimary() throws FilterParseException {
        if (match(TokenType.LPAREN)) {
            Expr e = parseOrExpr();
            if (!match(TokenType.RPAREN)) {
                throw new FilterParseException("Expected ')' at " + peek());
            }
            return e;
        }
        if (peek().getType() == TokenType.IDENTIFIER) {
            String id = advance().getValue();
            if ("IPLIKE".equalsIgnoreCase(id) && peek().getType() == TokenType.LPAREN) {
                advance(); // consume LPAREN
                Expr left = parsePrimary();
                if (!match(TokenType.COMMA)) {
                    throw new FilterParseException("Expected comma in IPLIKE(column, value) at " + peek());
                }
                Expr right = parsePrimary();
                if (!match(TokenType.RPAREN)) {
                    throw new FilterParseException("Expected ')' after IPLIKE(column, value) at " + peek());
                }
                return new IplikeExpr(left, right);
            }
            if (id.startsWith("catinc")) {
                return new CatincExpr(id.substring(6));
            }
            if (id.startsWith("is")) {
                return new IsServiceExpr(id.substring(2));
            }
            if (id.startsWith("notis")) {
                return new NotIsServiceExpr(id.substring(5));
            }
            return parseColumnRef(id);
        }
        if (peek().getType() == TokenType.PLACEHOLDER) {
            Token t = advance();
            return new PlaceholderExpr(t.getValue());
        }
        if (peek().getType() == TokenType.STRING) {
            Token t = advance();
            return new StringLiteralExpr(t.getValue());
        }
        throw new FilterParseException("Expected primary expression at " + peek());
    }

    private Token peek() {
        return index < tokens.size() ? tokens.get(index) : new Token(TokenType.EOF, "");
    }

    private Token peekNext() {
        return index + 1 < tokens.size() ? tokens.get(index + 1) : new Token(TokenType.EOF, "");
    }

    private Token advance() {
        if (index >= tokens.size()) {
            return new Token(TokenType.EOF, "");
        }
        return tokens.get(index++);
    }

    private boolean match(TokenType type) {
        if (peek().getType() == type) {
            advance();
            return true;
        }
        return false;
    }
}
