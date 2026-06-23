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
 * Node is in the given category (filter syntax: catinc&lt;categoryName&gt;).
 * Emits a single EXISTS subquery with categoryName = '...'.
 */
final class CatincExpr implements Expr {

    private final String categoryName;

    public CatincExpr(String categoryName) {
        this.categoryName = Objects.requireNonNull(categoryName, "categoryName");
    }

    public String getCategoryName() {
        return categoryName;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitCatinc(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatincExpr)) return false;
        CatincExpr that = (CatincExpr) o;
        return Objects.equals(categoryName, that.categoryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryName);
    }
}
