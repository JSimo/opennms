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

/**
 * Visitor over filter expression AST nodes.
 * Implementations can emit SQL, collect nodes, or transform the tree.
 *
 * @param <T> result type of each visit method
 */
interface ExprVisitor<T> {

    T visitAnd(AndExpr expr);

    T visitOr(OrExpr expr);

    T visitNot(NotExpr expr);

    T visitCatinc(CatincExpr expr);

    T visitCatincIn(CatincInExpr expr);

    T visitCatincAndIn(CatincAndInExpr expr);

    T visitCatincInExcept(CatincInExceptExpr expr);

    T visitCatincNotIn(CatincNotInExpr expr);

    T visitIsService(IsServiceExpr expr);

    T visitIsServiceIn(IsServiceInExpr expr);

    T visitNotIsService(NotIsServiceExpr expr);

    T visitColumnRef(ColumnRefExpr expr);

    T visitPlaceholder(PlaceholderExpr expr);

    T visitComparison(ComparisonExpr expr);

    T visitStringLiteral(StringLiteralExpr expr);

    T visitIplike(IplikeExpr expr);

    T visitIn(InExpr expr);

    T visitIsNull(IsNullExpr expr);
}
