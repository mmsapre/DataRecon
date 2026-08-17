package com.mms.data.recon.dataset;

public enum DatasourceType {
    postgres,
    mongo,
    bigquery,
    file;

    public boolean usesCalcite() {
        return this == bigquery || this == file;
    }
}
