package com.mms.data.recon.dataset;

import reactor.core.publisher.Flux;

public interface RowLoader {
    Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition, int batchSize);
}
