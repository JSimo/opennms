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
import java.util.List;
import java.util.stream.Collectors;

/** Removes duplicate terms in AND expressions. */
final class DeduplicateAndOptimizer extends BaseOptimizerStep {

    @Override
    protected Expr rewrite(Expr e) {
        if (e instanceof AndExpr) {
            List<Expr> rewritten = ((AndExpr) e).getChildren().stream()
                    .map(this::rewrite)
                    .collect(Collectors.toList());
            List<Expr> deduped = new ArrayList<>();
            for (Expr term : rewritten) {
                if (deduped.stream().noneMatch(existing -> existing.equals(term))) {
                    deduped.add(term);
                }
            }
            return deduped.size() == 1 ? deduped.get(0) : new AndExpr(deduped);
        }
        return recurse(e);
    }
}
