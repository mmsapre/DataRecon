package com.mms.data.recon;

import io.micronaut.runtime.Micronaut;

public final class ReconServer {
    private ReconServer() {}

    public static void main(String[] args) {
        Micronaut.run(ReconServer.class, args);
    }
}
