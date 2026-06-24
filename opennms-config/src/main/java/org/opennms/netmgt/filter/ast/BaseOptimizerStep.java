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

/**
 * Base for optimizer steps that provides a default recursive rewrite (rebuild node with rewritten children).
 * Subclasses override {@link #rewrite(Expr)} only for node types they transform.
 */
abstract class BaseOptimizerStep implements OptimizerStep {

    @Override
    public final Expr optimize(Expr root) {
        if (root == null) {
            return null;
        }
        return rewrite(root);
    }

    /**
     * Override in subclasses to apply a transformation; call {@link #recurse(Expr)} for pass-through.
     */
    protected Expr rewrite(Expr e) {
        return recurse(e);
    }

    /** Rebuild the expression with all children rewritten (post-order). */
    protected final Expr recurse(Expr e) {
        if (e instanceof AndExpr) {
            List<Expr> children = ((AndExpr) e).getChildren().stream()
                    .map(this::rewrite)
                    .collect(Collectors.toList());
            return children.size() == 1 ? children.get(0) : new AndExpr(children);
        }
        if (e instanceof OrExpr) {
            List<Expr> children = ((OrExpr) e).getChildren().stream()
                    .map(this::rewrite)
                    .collect(Collectors.toList());
            return children.size() == 1 ? children.get(0) : new OrExpr(children);
        }
        if (e instanceof NotExpr) {
            return new NotExpr(rewrite(((NotExpr) e).getChild()));
        }
        if (e instanceof ComparisonExpr) {
            ComparisonExpr comp = (ComparisonExpr) e;
            return new ComparisonExpr(rewrite(comp.getLeft()), comp.getOp(), rewrite(comp.getRight()));
        }
        if (e instanceof IplikeExpr) {
            IplikeExpr iplike = (IplikeExpr) e;
            return new IplikeExpr(rewrite(iplike.getLeft()), rewrite(iplike.getRight()));
        }
        if (e instanceof InExpr) {
            InExpr in = (InExpr) e;
            List<Expr> values = in.getValues().stream().map(this::rewrite).collect(Collectors.toList());
            return new InExpr(rewrite(in.getLeft()), values, in.isNotIn());
        }
        if (e instanceof IsNullExpr) {
            IsNullExpr isNull = (IsNullExpr) e;
            return new IsNullExpr(rewrite(isNull.getExpr()), isNull.isNotNull());
        }
        return e;
    }
}
