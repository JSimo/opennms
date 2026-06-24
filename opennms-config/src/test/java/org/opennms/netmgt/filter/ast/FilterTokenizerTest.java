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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.opennms.netmgt.filter.api.FilterParseException;

public class FilterTokenizerTest {

    @Test
    public void emptyInputProducesOnlyEof() throws FilterParseException {
        List<Token> tokens = new FilterTokenizer("").tokenize();
        assertNotNull(tokens);
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).getType());
    }

    @Test
    public void whitespaceOnlyProducesOnlyEof() throws FilterParseException {
        List<Token> tokens = new FilterTokenizer("   \t\n  ").tokenize();
        assertNotNull(tokens);
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).getType());
    }

    @Test
    public void catincOrCatincProducesExpectedTokens() throws FilterParseException {
        List<Token> tokens = new FilterTokenizer("catincRouters OR catincProduction").tokenize();
        assertNotNull(tokens);
        assertEquals(4, tokens.size()); // IDENTIFIER, OR, IDENTIFIER, EOF
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).getType());
        assertEquals("catincRouters", tokens.get(0).getValue());
        assertEquals(TokenType.OR, tokens.get(1).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).getType());
        assertEquals("catincProduction", tokens.get(2).getValue());
        assertEquals(TokenType.EOF, tokens.get(3).getType());
    }

    @Test
    public void parensAndAndOrNot() throws FilterParseException {
        List<Token> tokens = new FilterTokenizer("( catincA OR catincB ) AND NOT isICMP").tokenize();
        assertNotNull(tokens);
        assertEquals(9, tokens.size()); // LPAREN, catincA, OR, catincB, RPAREN, AND, NOT, isICMP, EOF
        assertEquals(TokenType.LPAREN, tokens.get(0).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).getType());
        assertEquals("catincA", tokens.get(1).getValue());
        assertEquals(TokenType.OR, tokens.get(2).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).getType());
        assertEquals("catincB", tokens.get(3).getValue());
        assertEquals(TokenType.RPAREN, tokens.get(4).getType());
        assertEquals(TokenType.AND, tokens.get(5).getType());
        assertEquals(TokenType.NOT, tokens.get(6).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(7).getType());
        assertEquals("isICMP", tokens.get(7).getValue());
        assertEquals(TokenType.EOF, tokens.get(8).getType());
    }

    @Test
    public void quotedStringBecomesPlaceholder() throws FilterParseException {
        FilterTokenizer tokenizer = new FilterTokenizer("nodeLabel = 'value'");
        List<Token> tokens = tokenizer.tokenize();
        assertNotNull(tokens);
        assertEquals(4, tokens.size()); // IDENTIFIER, EQ, PLACEHOLDER, EOF
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).getType());
        assertEquals("nodeLabel", tokens.get(0).getValue());
        assertEquals(TokenType.EQ, tokens.get(1).getType());
        assertEquals(TokenType.PLACEHOLDER, tokens.get(2).getType());
        assertEquals("0", tokens.get(2).getValue());
        assertEquals(TokenType.EOF, tokens.get(3).getType());
        assertEquals(1, tokenizer.getExtractedStrings().size());
        assertEquals("'value'", tokenizer.getExtractedStrings().get(0));
    }

    @Test
    public void multipleQuotedStringsGetSequentialPlaceholderIndices() throws FilterParseException {
        FilterTokenizer tokenizer = new FilterTokenizer("a = '1' AND b = '2'");
        List<Token> tokens = tokenizer.tokenize();
        assertEquals(TokenType.PLACEHOLDER, tokens.get(2).getType());
        assertEquals("0", tokens.get(2).getValue());
        assertEquals(TokenType.PLACEHOLDER, tokens.get(6).getType());
        assertEquals("1", tokens.get(6).getValue());
        assertEquals(2, tokenizer.getExtractedStrings().size());
        assertEquals("'1'", tokenizer.getExtractedStrings().get(0));
        assertEquals("'2'", tokenizer.getExtractedStrings().get(1));
    }

    @Test
    public void operatorAliasesAndOrNotEq() throws FilterParseException {
        List<Token> tokens = new FilterTokenizer("catincA && catincB").tokenize();
        assertNotNull(tokens);
        assertEquals(4, tokens.size());
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).getType());
        assertEquals(TokenType.AND, tokens.get(1).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).getType());
        assertEquals("catincB", tokens.get(2).getValue());

        tokens = new FilterTokenizer("catincA & catincB").tokenize();
        assertEquals(TokenType.AND, tokens.get(1).getType());

        tokens = new FilterTokenizer("catincA || catincB").tokenize();
        assertEquals(TokenType.OR, tokens.get(1).getType());

        tokens = new FilterTokenizer("! catincA").tokenize();
        assertEquals(TokenType.NOT, tokens.get(0).getType());

        tokens = new FilterTokenizer("x == 'v'").tokenize();
        assertEquals(TokenType.EQ, tokens.get(1).getType());

        tokens = new FilterTokenizer("x != 'v'").tokenize();
        assertEquals(TokenType.NE, tokens.get(1).getType());
        assertEquals("!=", tokens.get(1).getValue());
    }

    @Test
    public void commaAndQuotedStringProducesPlaceholder() throws FilterParseException {
        FilterTokenizer tokenizer = new FilterTokenizer("IPLIKE(ipAddr, '10.0.0.*')");
        List<Token> tokens = tokenizer.tokenize();
        assertNotNull(tokens);
        assertEquals(7, tokens.size()); // IPLIKE, (, ipAddr, ,, PLACEHOLDER, ), EOF
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).getType());
        assertEquals("IPLIKE", tokens.get(0).getValue());
        assertEquals(TokenType.LPAREN, tokens.get(1).getType());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).getType());
        assertEquals("ipAddr", tokens.get(2).getValue());
        assertEquals(TokenType.COMMA, tokens.get(3).getType());
        assertEquals(TokenType.PLACEHOLDER, tokens.get(4).getType());
        assertEquals("0", tokens.get(4).getValue());
        assertEquals(TokenType.RPAREN, tokens.get(5).getType());
        assertEquals(TokenType.EOF, tokens.get(6).getType());
        assertEquals(1, tokenizer.getExtractedStrings().size());
        assertEquals("'10.0.0.*'", tokenizer.getExtractedStrings().get(0));
    }

    @Test
    public void iplikeUnquotedPatternProducesIplikeThenStringTokens() throws FilterParseException {
        List<Token> tokens = new FilterTokenizer("ipAddr IPLIKE 10.0.0.*").tokenize();
        assertNotNull(tokens);
        assertEquals(4, tokens.size()); // ipAddr, IPLIKE, STRING(10.0.0.*), EOF
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).getType());
        assertEquals("ipAddr", tokens.get(0).getValue());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).getType());
        assertEquals("IPLIKE", tokens.get(1).getValue());
        assertEquals(TokenType.STRING, tokens.get(2).getType());
        assertEquals("10.0.0.*", tokens.get(2).getValue());
        assertEquals(TokenType.EOF, tokens.get(3).getType());
    }

    @Test(expected = FilterParseException.class)
    public void invalidCharacterThrows() throws FilterParseException {
        new FilterTokenizer("catincA % catincB").tokenize();
    }
}
