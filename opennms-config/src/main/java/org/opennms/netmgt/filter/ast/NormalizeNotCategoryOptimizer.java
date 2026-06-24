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

import java.util.List;
import java.util.stream.Collectors;

/** NOT(CatincExpr) / NOT(CatincInExpr) → CatincNotInExpr so the emitter has one shape for "node not in categories". */
final class NormalizeNotCategoryOptimizer extends BaseOptimizerStep {

    @Override
    protected Expr rewrite(Expr e) {
        if (e instanceof NotExpr) {
            Expr child = rewrite(((NotExpr) e).getChild());
            if (child instanceof CatincExpr) {
                return new CatincNotInExpr(List.of(((CatincExpr) child).getCategoryName()));
            }
            if (child instanceof CatincInExpr) {
                return new CatincNotInExpr(((CatincInExpr) child).getCategoryNames());
            }
            return new NotExpr(child);
        }
        return recurse(e);
    }
}
