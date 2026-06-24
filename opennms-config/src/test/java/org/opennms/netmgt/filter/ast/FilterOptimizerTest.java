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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.opennms.netmgt.filter.api.FilterParseException;

public class FilterOptimizerTest {

    private static Expr parseAndOptimize(String preprocessedRule) throws FilterParseException {
        List<Token> tokens = new FilterTokenizer(preprocessedRule).tokenize();
        Expr ast = new FilterParser(tokens).parse();
        return new FilterOptimizer().optimize(ast);
    }

    @Test
    public void orOfTwoCatincBecomesCatincIn() throws FilterParseException {
        Expr e = parseAndOptimize("catincRouters OR catincProduction");
        assertNotNull(e);
        assertTrue(e instanceof CatincInExpr);
        CatincInExpr in = (CatincInExpr) e;
        assertEquals(2, in.getCategoryNames().size());
        assertEquals("Routers", in.getCategoryNames().get(0));
        assertEquals("Production", in.getCategoryNames().get(1));
    }

    @Test
    public void orOfThreeCatincBecomesCatincIn() throws FilterParseException {
        Expr e = parseAndOptimize("catincA OR catincB OR catincC");
        assertNotNull(e);
        assertTrue(e instanceof CatincInExpr);
        CatincInExpr in = (CatincInExpr) e;
        assertEquals(3, in.getCategoryNames().size());
        assertEquals("A", in.getCategoryNames().get(0));
        assertEquals("B", in.getCategoryNames().get(1));
        assertEquals("C", in.getCategoryNames().get(2));
    }

    @Test
    public void singleCatincStaysCatincExpr() throws FilterParseException {
        Expr e = parseAndOptimize("catincRouters");
        assertNotNull(e);
        assertTrue(e instanceof CatincExpr);
        assertEquals("Routers", ((CatincExpr) e).getCategoryName());
    }

    @Test
    public void orOfCatincAndNonCatincStaysOrExpr() throws FilterParseException {
        Expr e = parseAndOptimize("catincRouters OR isICMP");
        assertNotNull(e);
        assertTrue(e instanceof OrExpr);
        OrExpr or = (OrExpr) e;
        assertEquals(2, or.getChildren().size());
        assertTrue(or.getChildren().get(0) instanceof CatincExpr);
        assertTrue(or.getChildren().get(1) instanceof IsServiceExpr);
    }

    @Test
    public void orOfTwoIsServiceBecomesIsServiceIn() throws FilterParseException {
        Expr e = parseAndOptimize("isICMP OR isSNMP");
        assertNotNull(e);
        assertTrue(e instanceof IsServiceInExpr);
        IsServiceInExpr in = (IsServiceInExpr) e;
        assertEquals(2, in.getServiceNames().size());
        assertEquals("ICMP", in.getServiceNames().get(0));
        assertEquals("SNMP", in.getServiceNames().get(1));
    }

    @Test
    public void orOfThreeIsServiceBecomesIsServiceIn() throws FilterParseException {
        Expr e = parseAndOptimize("isICMP OR isSNMP OR isHTTP");
        assertNotNull(e);
        assertTrue(e instanceof IsServiceInExpr);
        IsServiceInExpr in = (IsServiceInExpr) e;
        assertEquals(3, in.getServiceNames().size());
    }

    @Test
    public void orOfIsServiceAndCatincStaysOrExpr() throws FilterParseException {
        Expr e = parseAndOptimize("isICMP OR catincRouters");
        assertNotNull(e);
        assertTrue(e instanceof OrExpr);
        assertEquals(2, ((OrExpr) e).getChildren().size());
    }

    @Test
    public void andOfOrCatincAndIsServiceOptimizesInnerOr() throws FilterParseException {
        Expr e = parseAndOptimize("( catincA OR catincB ) AND isICMP");
        assertNotNull(e);
        assertTrue(e instanceof AndExpr);
        AndExpr and = (AndExpr) e;
        assertEquals(2, and.getChildren().size());
        assertTrue(and.getChildren().get(0) instanceof CatincInExpr);
        assertTrue(and.getChildren().get(1) instanceof IsServiceExpr);
        CatincInExpr in = (CatincInExpr) and.getChildren().get(0);
        assertEquals(2, in.getCategoryNames().size());
        assertEquals("A", in.getCategoryNames().get(0));
        assertEquals("B", in.getCategoryNames().get(1));
    }

    @Test
    public void notCatincBecomesCatincNotIn() throws FilterParseException {
        Expr e = parseAndOptimize("!catincDMZ");
        assertNotNull(e);
        assertTrue(e instanceof CatincNotInExpr);
        assertEquals(List.of("DMZ"), ((CatincNotInExpr) e).getCategoryNames());
    }

    @Test
    public void optimizeNullReturnsNull() {
        assertSame(null, new FilterOptimizer().optimize(null));
    }

    @Test
    public void leafExprUnchanged() throws FilterParseException {
        Expr e = parseAndOptimize("nodeLabel = 'value'");
        assertNotNull(e);
        assertTrue(e instanceof ComparisonExpr);
        assertTrue(((ComparisonExpr) e).getLeft() instanceof ColumnRefExpr);
        assertTrue(((ComparisonExpr) e).getRight() instanceof PlaceholderExpr);
    }

    @Test
    public void flattenNestedAnd() throws FilterParseException {
        Expr e = parseAndOptimize("(catincA AND isICMP) AND catincB");
        assertNotNull(e);
        assertTrue(e instanceof AndExpr);
        assertEquals(3, ((AndExpr) e).getChildren().size());
    }

    @Test
    public void deduplicateAndTerms() throws FilterParseException {
        Expr e = parseAndOptimize("(catincA OR catincB) AND (catincA OR catincB) AND isICMP");
        assertNotNull(e);
        assertTrue(e instanceof AndExpr);
        AndExpr and = (AndExpr) e;
        assertEquals(2, and.getChildren().size());
        assertTrue(and.getChildren().get(0) instanceof CatincInExpr);
        assertTrue(and.getChildren().get(1) instanceof IsServiceExpr);
    }

    @Test
    public void andOfTwoCatincInBecomesCatincAndIn() throws FilterParseException {
        Expr e = parseAndOptimize("(catincA OR catincB) AND (catincC OR catincD)");
        assertNotNull(e);
        assertTrue(e instanceof CatincAndInExpr);
        CatincAndInExpr andIn = (CatincAndInExpr) e;
        assertEquals(2, andIn.getCategoryGroups().size());
        assertEquals(List.of("A", "B"), andIn.getCategoryGroups().get(0));
        assertEquals(List.of("C", "D"), andIn.getCategoryGroups().get(1));
    }

    @Test
    public void andOfCatincInAndCatincBecomesCatincAndIn() throws FilterParseException {
        Expr e = parseAndOptimize("(catincA OR catincB) AND catincC");
        assertNotNull(e);
        assertTrue(e instanceof CatincAndInExpr);
        CatincAndInExpr andIn = (CatincAndInExpr) e;
        assertEquals(2, andIn.getCategoryGroups().size());
        assertEquals(List.of("A", "B"), andIn.getCategoryGroups().get(0));
        assertEquals(List.of("C"), andIn.getCategoryGroups().get(1));
    }

    @Test
    public void orFlattenedBeforeCatincInMerge() throws FilterParseException {
        Expr e = parseAndOptimize("(catincA OR catincB) OR catincC");
        assertNotNull(e);
        assertTrue(e instanceof CatincInExpr);
        assertEquals(3, ((CatincInExpr) e).getCategoryNames().size());
    }

    @Test
    public void andOfCatincInAndNotCatincBecomesCatincInExcept() throws FilterParseException {
        Expr e = parseAndOptimize("(catincProduction OR catincSWITCH) AND !catincCUST AND isICMP");
        assertNotNull(e);
        assertTrue(e instanceof AndExpr);
        AndExpr and = (AndExpr) e;
        assertEquals(2, and.getChildren().size());
        assertTrue("category IN and NOT IN merged into CatincInExceptExpr", and.getChildren().get(0) instanceof CatincInExceptExpr);
        CatincInExceptExpr except = (CatincInExceptExpr) and.getChildren().get(0);
        assertEquals(List.of(List.of("Production", "SWITCH")), except.getPositiveGroups());
        assertEquals(List.of("CUST"), except.getNegativeNames());
        assertTrue(and.getChildren().get(1) instanceof IsServiceExpr);
    }
}
