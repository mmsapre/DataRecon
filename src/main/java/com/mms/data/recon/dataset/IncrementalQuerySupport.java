package com.mms.data.recon.dataset;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Query-driven incremental window. When a side query contains {@code :since} / {@code :until}
 * (SQL) or {@code ":since"} / {@code ":until"} (Mongo JSON), those tokens are replaced with
 * {@code ?} and bound to the previous active run time → now on INCREMENTAL runs
 * (or epoch → now on FULL). Queries without those tokens are left unchanged.
 * <p>
 * Works with hand-written DISTINCT / single-or-multiple identifier SELECTs the same way —
 * the query defines the window; profile config does not append filters.
 */
public final class IncrementalQuerySupport {

    public static final String SINCE = ":since";
    public static final String UNTIL = ":until";

    private IncrementalQuerySupport() {}

    /**
     * @param since lower bound (exclusive conceptually for typical {@code > :since} predicates);
     *              use {@link Instant#EPOCH} for FULL extracts
     * @param until upper bound (typically now)
     * @return a copy with tokens expanded, or the original side when no tokens are present
     */
    public static DataLoadDefinition applyWindow(
            DataLoadDefinition side,
            DatasourceType type,
            Instant since,
            Instant until) {
        if (side == null) {
            return null;
        }
        Instant from = since == null ? Instant.EPOCH : since;
        Instant to = until == null ? Instant.now() : until;
        if (to.isBefore(from)) {
            to = from;
        }

        String statement = configuredStatement(side, type);
        if (statement == null || !needsWindow(type, statement)) {
            return side;
        }

        DataLoadDefinition copy = copyOf(side);
        List<Object> baseParams = PreparedQueries.copy(copy.getQueryParams());
        Object sinceValue = type == DatasourceType.mongo ? from.toString() : Timestamp.from(from);
        Object untilValue = type == DatasourceType.mongo ? to.toString() : Timestamp.from(to);

        Bound bound = type == DatasourceType.mongo
                ? expandMongoNamedPlaceholders(statement, baseParams, sinceValue, untilValue)
                : expandNamedPlaceholders(statement, baseParams, sinceValue, untilValue);
        copy.setQuery(bound.sql());
        copy.setQueryParams(bound.params());
        return copy;
    }

    /**
     * Only configured query text (inline or queryFile) can carry {@code :since}/{@code :until}.
     * Generated table SELECT / default Mongo {@code {}} never do.
     */
    private static String configuredStatement(DataLoadDefinition side, DatasourceType type) {
        String inline = side.getQuery();
        if (inline != null && !inline.isBlank()) {
            return inline.trim();
        }
        if (side.getQueryFile() != null) {
            return side.resolveQueryStatement(type);
        }
        return null;
    }

    public static boolean needsWindow(DatasourceType type, String statement) {
        if (statement == null || statement.isEmpty()) {
            return false;
        }
        if (type == DatasourceType.mongo) {
            String lower = statement.toLowerCase(Locale.ROOT);
            return lower.contains("\":since\"") || lower.contains("\":until\"")
                    || containsNamedPlaceholder(statement);
        }
        return containsNamedPlaceholder(statement);
    }

    static boolean containsNamedPlaceholder(String sql) {
        return indexOfToken(sql, SINCE) >= 0 || indexOfToken(sql, UNTIL) >= 0;
    }

    static Bound expandNamedPlaceholders(
            String sql,
            List<Object> existingParams,
            Object sinceValue,
            Object untilValue) {
        List<Object> namedValues = new ArrayList<>();
        StringBuilder out = new StringBuilder(sql.length() + 8);
        int i = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                out.append(c);
                i++;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                out.append(c);
                i++;
                continue;
            }
            if (!inSingle && !inDouble && c == ':') {
                if (matchesToken(sql, i, SINCE)) {
                    out.append('?');
                    namedValues.add(sinceValue);
                    i += SINCE.length();
                    continue;
                }
                if (matchesToken(sql, i, UNTIL)) {
                    out.append('?');
                    namedValues.add(untilValue);
                    i += UNTIL.length();
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        List<Object> params = new ArrayList<>(namedValues.size() + existingParams.size());
        params.addAll(namedValues);
        params.addAll(existingParams);
        return new Bound(out.toString(), params);
    }

    static Bound expandMongoNamedPlaceholders(
            String json,
            List<Object> existingParams,
            Object sinceValue,
            Object untilValue) {
        List<Object> namedValues = new ArrayList<>();
        StringBuilder out = new StringBuilder(json.length() + 8);
        int i = 0;
        while (i < json.length()) {
            if (i + 8 <= json.length() && json.regionMatches(true, i, "\":since\"", 0, 8)) {
                out.append("\"?\"");
                namedValues.add(sinceValue);
                i += 8;
                continue;
            }
            if (i + 8 <= json.length() && json.regionMatches(true, i, "\":until\"", 0, 8)) {
                out.append("\"?\"");
                namedValues.add(untilValue);
                i += 8;
                continue;
            }
            if (matchesToken(json, i, SINCE)) {
                out.append('?');
                namedValues.add(sinceValue);
                i += SINCE.length();
                continue;
            }
            if (matchesToken(json, i, UNTIL)) {
                out.append('?');
                namedValues.add(untilValue);
                i += UNTIL.length();
                continue;
            }
            out.append(json.charAt(i));
            i++;
        }
        List<Object> params = new ArrayList<>(namedValues.size() + existingParams.size());
        params.addAll(namedValues);
        params.addAll(existingParams);
        return new Bound(out.toString(), params);
    }

    private static int indexOfToken(String sql, String token) {
        int from = 0;
        String lower = sql.toLowerCase(Locale.ROOT);
        String needle = token.toLowerCase(Locale.ROOT);
        while (from < sql.length()) {
            int at = lower.indexOf(needle, from);
            if (at < 0) {
                return -1;
            }
            if (matchesToken(sql, at, token)) {
                return at;
            }
            from = at + 1;
        }
        return -1;
    }

    private static boolean matchesToken(String sql, int index, String token) {
        if (index + token.length() > sql.length()) {
            return false;
        }
        if (!sql.regionMatches(true, index, token, 0, token.length())) {
            return false;
        }
        int end = index + token.length();
        if (end < sql.length()) {
            char next = sql.charAt(end);
            if (Character.isLetterOrDigit(next) || next == '_') {
                return false;
            }
        }
        return true;
    }

    private static DataLoadDefinition copyOf(DataLoadDefinition source) {
        DataLoadDefinition copy = new DataLoadDefinition();
        copy.setDatasource(source.getDatasource());
        copy.setDatasourceRef(source.getDatasourceRef());
        copy.setType(source.getType());
        copy.setQuery(source.getQuery());
        copy.setQueryFile(source.getQueryFile());
        copy.setQueryParams(source.getQueryParams());
        copy.setCollection(source.getCollection());
        copy.setSchema(source.getSchema());
        copy.setTable(source.getTable());
        copy.setMigrationKey(source.getMigrationKey());
        copy.setIdentifiers(source.getIdentifiers());
        copy.setFields(source.getFields());
        copy.setDistinct(source.isDistinct());
        copy.initialize(source.getDatasetId(), source.getRole(), Path.of("queries"));
        return copy;
    }

    record Bound(String sql, List<Object> params) {}
}
