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
 * Placeholder for an extracted string (e.g. quoted value) from the filter rule.
 * The index refers to a slot in the extractedStrings list; emission replaces
 * this with the actual string for the final SQL.
 */
final class PlaceholderExpr implements Expr {

    private final String index;

    public PlaceholderExpr(String index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    /**
     * Index into the extracted-strings list (as string, e.g. "0", "1").
     */
    public String getIndex() {
        return index;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitPlaceholder(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaceholderExpr)) return false;
        PlaceholderExpr that = (PlaceholderExpr) o;
        return Objects.equals(index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }
}
