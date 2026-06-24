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
 * Interface supports any of the given services (optimized form of OR of IsServiceExpr).
 * Emits serviceName IN ('A','B',...) instead of (serviceName = 'A' OR serviceName = 'B' OR ...).
 */
final class IsServiceInExpr implements Expr {

    private final List<String> serviceNames;

    public IsServiceInExpr(List<String> serviceNames) {
        Objects.requireNonNull(serviceNames, "serviceNames");
        List<String> copy = new ArrayList<>(serviceNames);
        for (String s : copy) {
            Objects.requireNonNull(s, "service name");
        }
        this.serviceNames = Collections.unmodifiableList(copy);
    }

    public List<String> getServiceNames() {
        return serviceNames;
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitIsServiceIn(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IsServiceInExpr)) return false;
        IsServiceInExpr that = (IsServiceInExpr) o;
        return Objects.equals(serviceNames, that.serviceNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceNames);
    }
}
