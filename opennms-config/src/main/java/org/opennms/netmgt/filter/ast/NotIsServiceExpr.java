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
 * Interface does not support the given service (filter syntax: notis&lt;serviceName&gt;).
 * Emits ipAddr NOT IN (SELECT ... WHERE serviceName = '...') in SQL.
 */
final class NotIsServiceExpr implements Expr {

    private final String serviceName;

    public NotIsServiceExpr(String serviceName) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitNotIsService(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotIsServiceExpr)) return false;
        NotIsServiceExpr that = (NotIsServiceExpr) o;
        return Objects.equals(serviceName, that.serviceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName);
    }
}
