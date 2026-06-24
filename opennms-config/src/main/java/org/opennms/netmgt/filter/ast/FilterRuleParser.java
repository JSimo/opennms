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

import org.opennms.netmgt.config.api.DatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.Table;
import org.opennms.netmgt.filter.api.FilterParseException;

/**
 * Public entry point for the filter AST pipeline (tokenizer → parser → optimizer → SQL emitter).
 */
public final class FilterRuleParser {

    private FilterRuleParser() {
    }

    /**
     * Parse a filter rule into a SQL WHERE clause body (without the {@code WHERE} keyword).
     *
     * @return SQL expression, or empty string when the rule is empty
     */
    public static String parseWhereBody(final List<Table> tables, final String rule,
            final DatabaseSchemaConfig schema) throws FilterParseException {
        if (rule == null || rule.isEmpty()) {
            return "";
        }
        FilterTokenizer tokenizer = new FilterTokenizer(rule);
        List<Token> tokens = tokenizer.tokenize();
        List<String> extractedStrings = tokenizer.getExtractedStrings();

        Expr ast = new FilterParser(tokens).parse();
        if (ast == null) {
            return "";
        }
        ast = new FilterOptimizer().optimize(ast);
        return ast.accept(new SqlEmitter(tables, schema, extractedStrings));
    }

    /**
     * Parse without optimizer merges — used by parity tests to compare grammar coverage with the legacy parser.
     */
    public static String parseWhereBodyUnoptimized(final List<Table> tables, final String rule,
            final DatabaseSchemaConfig schema) throws FilterParseException {
        if (rule == null || rule.isEmpty()) {
            return "";
        }
        FilterTokenizer tokenizer = new FilterTokenizer(rule);
        List<Token> tokens = tokenizer.tokenize();
        List<String> extractedStrings = tokenizer.getExtractedStrings();

        Expr ast = new FilterParser(tokens).parse();
        if (ast == null) {
            return "";
        }
        return ast.accept(new SqlEmitter(tables, schema, extractedStrings));
    }

    /**
     * Parse a filter rule into a full SQL WHERE clause (including the {@code WHERE} keyword).
     */
    public static String parseWhere(final List<Table> tables, final String rule,
            final DatabaseSchemaConfig schema) throws FilterParseException {
        String body = parseWhereBody(tables, rule, schema);
        return body.isEmpty() ? "" : "WHERE " + body;
    }

    /**
     * Parse without optimizer merges into a full WHERE clause (parity tests).
     */
    public static String parseWhereUnoptimized(final List<Table> tables, final String rule,
            final DatabaseSchemaConfig schema) throws FilterParseException {
        String body = parseWhereBodyUnoptimized(tables, rule, schema);
        return body.isEmpty() ? "" : "WHERE " + body;
    }
}
