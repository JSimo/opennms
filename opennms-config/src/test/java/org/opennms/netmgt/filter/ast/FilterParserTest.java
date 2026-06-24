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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.opennms.netmgt.filter.api.FilterParseException;

public class FilterParserTest {

    private static Expr parse(String preprocessedRule) throws FilterParseException {
        List<Token> tokens = new FilterTokenizer(preprocessedRule).tokenize();
        return new FilterParser(tokens).parse();
    }

    @Test
    public void emptyInputReturnsNull() throws FilterParseException {
        assertNull(parse(""));
    }

    @Test
    public void singleCatincProducesCatincExpr() throws FilterParseException {
        Expr e = parse("catincRouters");
        assertNotNull(e);
        assertTrue(e instanceof CatincExpr);
        assertEquals("Routers", ((CatincExpr) e).getCategoryName());
    }

    @Test
    public void catincOrCatincProducesOrOfTwoCatinc() throws FilterParseException {
        Expr e = parse("catincRouters OR catincProduction");
        assertNotNull(e);
        assertTrue(e instanceof OrExpr);
        OrExpr or = (OrExpr) e;
        assertEquals(2, or.getChildren().size());
        assertTrue(or.getChildren().get(0) instanceof CatincExpr);
        assertTrue(or.getChildren().get(1) instanceof CatincExpr);
        assertEquals("Routers", ((CatincExpr) or.getChildren().get(0)).getCategoryName());
        assertEquals("Production", ((CatincExpr) or.getChildren().get(1)).getCategoryName());
    }

    @Test
    public void parensAndOrAndIsService() throws FilterParseException {
        Expr e = parse("( catincA OR catincB ) AND isICMP");
        assertNotNull(e);
        assertTrue(e instanceof AndExpr);
        AndExpr and = (AndExpr) e;
        assertEquals(2, and.getChildren().size());
        assertTrue(and.getChildren().get(0) instanceof OrExpr);
        assertTrue(and.getChildren().get(1) instanceof IsServiceExpr);
        OrExpr or = (OrExpr) and.getChildren().get(0);
        assertEquals(2, or.getChildren().size());
        assertEquals("ICMP", ((IsServiceExpr) and.getChildren().get(1)).getServiceName());
    }

    @Test
    public void notCatincProducesNotExpr() throws FilterParseException {
        Expr e = parse("NOT catincDMZ");
        assertNotNull(e);
        assertTrue(e instanceof NotExpr);
        assertTrue(((NotExpr) e).getChild() instanceof CatincExpr);
        assertEquals("DMZ", ((CatincExpr) ((NotExpr) e).getChild()).getCategoryName());
    }

    @Test
    public void notisProducesNotIsServiceExpr() throws FilterParseException {
        Expr e = parse("notisHTTP");
        assertNotNull(e);
        assertTrue(e instanceof NotIsServiceExpr);
        assertEquals("HTTP", ((NotIsServiceExpr) e).getServiceName());
    }

    @Test
    public void columnEqualsPlaceholderProducesComparisonExpr() throws FilterParseException {
        Expr e = parse("nodeLabel = 'value'");
        assertNotNull(e);
        assertTrue(e instanceof ComparisonExpr);
        ComparisonExpr comp = (ComparisonExpr) e;
        assertTrue(comp.getLeft() instanceof ColumnRefExpr);
        assertEquals("nodeLabel", ((ColumnRefExpr) comp.getLeft()).getColumnName());
        assertEquals("=", comp.getOp());
        assertTrue(comp.getRight() instanceof PlaceholderExpr);
        assertEquals("0", ((PlaceholderExpr) comp.getRight()).getIndex());
    }

    @Test
    public void bareColumnRefProducesColumnRefExpr() throws FilterParseException {
        Expr e = parse("nodeLabel");
        assertNotNull(e);
        assertTrue(e instanceof ColumnRefExpr);
        assertEquals("nodeLabel", ((ColumnRefExpr) e).getColumnName());
    }

    @Test
    public void placeholderOnlyProducesPlaceholderExpr() throws FilterParseException {
        Expr e = parse("'x'");
        assertNotNull(e);
        assertTrue(e instanceof PlaceholderExpr);
        assertEquals("0", ((PlaceholderExpr) e).getIndex());
    }

    @Test(expected = FilterParseException.class)
    public void unmatchedParenThrows() throws FilterParseException {
        parse("( catincA");
    }

    @Test
    public void comparisonNotEquals() throws FilterParseException {
        Expr e = parse("nodeLabel != 'value'");
        assertNotNull(e);
        assertTrue(e instanceof ComparisonExpr);
        ComparisonExpr comp = (ComparisonExpr) e;
        assertEquals("!=", comp.getOp());
    }

    @Test
    public void columnLikePattern() throws FilterParseException {
        Expr e = parse("nodeSysOID LIKE '.1.3.6.1.4.1.6527.1.3.%'");
        assertNotNull(e);
        assertTrue(e instanceof ComparisonExpr);
        ComparisonExpr comp = (ComparisonExpr) e;
        assertEquals("LIKE", comp.getOp());
        assertTrue(comp.getLeft() instanceof ColumnRefExpr);
        assertEquals("nodeSysOID", ((ColumnRefExpr) comp.getLeft()).getColumnName());
        assertTrue(comp.getRight() instanceof PlaceholderExpr);
    }

    @Test
    public void columnNotLikePattern() throws FilterParseException {
        Expr e = parse("nodeLabel NOT LIKE 'test%'");
        assertNotNull(e);
        assertTrue(e instanceof ComparisonExpr);
        ComparisonExpr comp = (ComparisonExpr) e;
        assertEquals("NOT LIKE", comp.getOp());
    }

    @Test
    public void columnInList() throws FilterParseException {
        Expr e = parse("nodeLabel IN ('a', 'b', 'c')");
        assertNotNull(e);
        assertTrue(e instanceof InExpr);
        InExpr in = (InExpr) e;
        assertTrue(in.getLeft() instanceof ColumnRefExpr);
        assertEquals(3, in.getValues().size());
    }

    @Test
    public void columnIsNull() throws FilterParseException {
        Expr e = parse("nodeLabel IS NULL");
        assertNotNull(e);
        assertTrue(e instanceof IsNullExpr);
        IsNullExpr isNull = (IsNullExpr) e;
        assertFalse(isNull.isNotNull());
        assertTrue(isNull.getExpr() instanceof ColumnRefExpr);
    }

    @Test
    public void columnIsNotNull() throws FilterParseException {
        Expr e = parse("nodeLabel IS NOT NULL");
        assertNotNull(e);
        assertTrue(e instanceof IsNullExpr);
        IsNullExpr isNull = (IsNullExpr) e;
        assertTrue(isNull.isNotNull());
    }

    @Test
    public void catincOrCatincAndNodeSysOidLike() throws FilterParseException {
        // Rule that failed in production: (catincProduction | catincUNVERIFIED) & (nodeSysOID LIKE '.1.3.6.1.4.1.6527.1.3.%')
        Expr e = parse("(catincProduction | catincUNVERIFIED) & (nodeSysOID LIKE '.1.3.6.1.4.1.6527.1.3.%')");
        assertNotNull(e);
        assertTrue(e instanceof AndExpr);
        AndExpr and = (AndExpr) e;
        assertEquals(2, and.getChildren().size());
        assertTrue(and.getChildren().get(0) instanceof OrExpr);
        assertTrue(and.getChildren().get(1) instanceof ComparisonExpr);
        ComparisonExpr like = (ComparisonExpr) and.getChildren().get(1);
        assertEquals("LIKE", like.getOp());
    }

    @Test
    public void andWithAmpersand() throws FilterParseException {
        Expr e = parse("catincA && catincB");
        assertNotNull(e);
        assertTrue(e instanceof AndExpr);
        AndExpr and = (AndExpr) e;
        assertEquals(2, and.getChildren().size());
        assertTrue(and.getChildren().get(0) instanceof CatincExpr);
        assertTrue(and.getChildren().get(1) instanceof CatincExpr);
    }

    @Test
    public void iplikeOperatorFormWithPlaceholder() throws FilterParseException {
        // "col IPLIKE value" (operator form) with quoted value → PlaceholderExpr
        Expr e = parse("ipAddr IPLIKE '10.0.0.*'");
        assertNotNull(e);
        assertTrue(e instanceof IplikeExpr);
        IplikeExpr iplike = (IplikeExpr) e;
        assertTrue(iplike.getLeft() instanceof ColumnRefExpr);
        assertEquals("ipAddr", ((ColumnRefExpr) iplike.getLeft()).getColumnName());
        assertTrue(iplike.getRight() instanceof PlaceholderExpr);
        assertEquals("0", ((PlaceholderExpr) iplike.getRight()).getIndex());
    }

    @Test
    public void iplikeWithPlaceholder() throws FilterParseException {
        Expr e = parse("IPLIKE(ipAddr, '10.0.0.*')");
        assertNotNull(e);
        assertTrue(e instanceof IplikeExpr);
        IplikeExpr iplike = (IplikeExpr) e;
        assertTrue(iplike.getLeft() instanceof ColumnRefExpr);
        assertEquals("ipAddr", ((ColumnRefExpr) iplike.getLeft()).getColumnName());
        assertTrue(iplike.getRight() instanceof PlaceholderExpr);
        assertEquals("0", ((PlaceholderExpr) iplike.getRight()).getIndex());
    }

    @Test
    public void iplikeWithStringLiteral() throws FilterParseException {
        // Unquoted pattern in "col IPLIKE pattern" becomes STRING token → StringLiteralExpr
        Expr e = parse("ipAddr IPLIKE 10.0.0.*");
        assertNotNull(e);
        assertTrue(e instanceof IplikeExpr);
        IplikeExpr iplike = (IplikeExpr) e;
        assertTrue(iplike.getRight() instanceof StringLiteralExpr);
        assertEquals("10.0.0.*", ((StringLiteralExpr) iplike.getRight()).getValue());
    }

    @Test(expected = FilterParseException.class)
    public void unexpectedTokenAtEndThrows() throws FilterParseException {
        parse("catincA OR");
    }

    @Test
    public void isDistinctFrom() throws FilterParseException {
        Expr e = parse("nodeLabel IS DISTINCT FROM 'other'");
        assertTrue(e instanceof ComparisonExpr);
        assertEquals("IS DISTINCT FROM", ((ComparisonExpr) e).getOp());
    }

    @Test
    public void isNotDistinctFrom() throws FilterParseException {
        Expr e = parse("nodeLabel IS NOT DISTINCT FROM 'other'");
        assertTrue(e instanceof ComparisonExpr);
        assertEquals("IS NOT DISTINCT FROM", ((ComparisonExpr) e).getOp());
    }

    @Test
    public void columnNotIn() throws FilterParseException {
        Expr e = parse("serviceName NOT IN ('ICMP', 'SNMP')");
        assertTrue(e instanceof InExpr);
        assertTrue(((InExpr) e).isNotIn());
    }

    @Test
    public void columnWithInetCast() throws FilterParseException {
        Expr e = parse("ipAddr::INET");
        assertTrue(e instanceof ColumnRefExpr);
        assertEquals("INET", ((ColumnRefExpr) e).getTypeCast());
    }
}
