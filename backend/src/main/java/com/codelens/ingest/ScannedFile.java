package com.codelens.ingest;

public record ScannedFile(String path, String module, String sha256, long size, boolean test) {
}
