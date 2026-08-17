package com.mms.data.recon.dataset;

import com.mms.data.recon.config.FileDatasourceProperties;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

import java.util.Map;

public final class CalciteFileSchema extends AbstractSchema {

    private final FileDatasourceProperties properties;

    public CalciteFileSchema(FileDatasourceProperties properties) {
        this.properties = properties;
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return Map.of(properties.resolveTableName(), new FileScannableTable(properties));
    }
}
