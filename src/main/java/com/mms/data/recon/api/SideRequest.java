package com.mms.data.recon.api;

import com.mms.data.recon.dataset.DataLoadDefinition;
import com.mms.data.recon.dataset.DatasourceType;

import java.util.List;

/**
 * Source or target side of a profile. Datasource is a catalog name
 * ({@code landing}, {@code csv}, …), not a new connection definition.
 * Optional {@code query} is PostgreSQL/BigQuery SQL or a Mongo JSON filter.
 * Optional {@code queryParams} binds positional {@code ?} placeholders.
 */
public class SideRequest {

    private String datasource;
    private DatasourceType type;
    private String schema;
    private String table;
    private String collection;
    private List<String> fields;
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
        if (fields != null) {
            side.setFields(fields);
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

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<Object> getQueryParams() { return queryParams; }
    public void setQueryParams(List<Object> queryParams) { this.queryParams = queryParams; }
}
