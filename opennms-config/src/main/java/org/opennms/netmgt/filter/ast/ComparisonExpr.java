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

import java.util.Objects;

/**
 * Binary comparison: left op right (e.g. nodeLabel = 'x').
 * Used for column = value, column LIKE value, etc.
 */
final class ComparisonExpr implements Expr {

    private final Expr left;
    private final String op;
    private final Expr right;

    public ComparisonExpr(Expr left, String op, Expr right) {
        this.left = Objects.requireNonNull(left, "left");
        this.op = Objects.requireNonNull(op, "op");
        this.right = Objects.requireNonNull(right, "right");
    }

    public Expr getLeft() {
        return left;
    }

    public String getOp() {
        return op;
    }

    public Expr getRight() {
        return right;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitComparison(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComparisonExpr)) return false;
        ComparisonExpr that = (ComparisonExpr) o;
        return Objects.equals(left, that.left) && Objects.equals(op, that.op) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, op, right);
    }
}
