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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * OR of two or more expressions: (child1 OR child2 OR ...).
 */
final class OrExpr implements Expr {

    private final List<Expr> children;

    public OrExpr(List<Expr> children) {
        Objects.requireNonNull(children, "children");
        List<Expr> copy = new ArrayList<>(children);
        for (Expr e : copy) {
            Objects.requireNonNull(e, "child");
        }
        this.children = Collections.unmodifiableList(copy);
    }

    public List<Expr> getChildren() {
        return children;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitOr(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrExpr)) return false;
        OrExpr that = (OrExpr) o;
        return Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(children);
    }
}
