package com.mms.data.recon.api;

import com.mms.data.recon.dataset.DataLoadDefinition;
import com.mms.data.recon.dataset.DatasourceType;

import java.util.List;

/**
 * Source or target side of a profile. Datasource is a catalog name
 * ({@code landing}, {@code csv}, …), not a new connection definition.
 * Prefer table/{@code identifiers}/{@code fields} mapping, or set {@code query} (plain or conditional SQL /
 * Mongo JSON filter) with MigrationKey + optional identifiers + fields. {@code COUNTS} hashes those
 * fields with the profile hashing strategy; detail modes send the same rows to DuckDB and apply
 * TypeLenient/TypeStrict normalization there too. Schema usually comes from the datasource URI.
 * Optional {@code queryParams} binds positional {@code ?} placeholders.
 * Optional {@code :since} / {@code :until} in the query enable FULL (epoch→now) and INCREMENTAL
 * (previous active run→now) date windows without a separate profile date-field setting.
 */
public class SideRequest {

    private String datasource;
    private DatasourceType type;
    private String schema;
    private String table;
    private String collection;
    private List<String> identifiers;
    private List<String> fields;
    private Boolean distinct;
    private String query;
    private List<Object> queryParams;

    public void applyTo(DataLoadDefinition side) {
        if (datasource != null) {
            side.attachDatasource(datasource);
        }
        if (type != null) {
            side.setType(type);
        }
        if (schema != null) {
            side.setSchema(schema);
        }
        if (table != null) {
            side.setTable(table);
        }
        if (collection != null) {
            side.setCollection(collection);
        }
        if (identifiers != null) {
            side.setIdentifiers(identifiers);
        }
        if (fields != null) {
            side.setFields(fields);
        }
        if (distinct != null) {
            side.setDistinct(distinct);
        }
        if (query != null) {
            side.setQuery(query);
        }
        if (queryParams != null) {
            side.setQueryParams(queryParams);
        }
    }

    public DataLoadDefinition toDefinition() {
        DataLoadDefinition side = new DataLoadDefinition();
        applyTo(side);
        return side;
    }

    public String getDatasource() { return datasource; }
    public void setDatasource(String datasource) { this.datasource = datasource; }

    public DatasourceType getType() { return type; }
    public void setType(DatasourceType type) { this.type = type; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }

    public Boolean getDistinct() { return distinct; }
    public void setDistinct(Boolean distinct) { this.distinct = distinct; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<Object> getQueryParams() { return queryParams; }
    public void setQueryParams(List<Object> queryParams) { this.queryParams = queryParams; }
}
