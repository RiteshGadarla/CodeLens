package com.codelens.repository;

public record FileRow(String path, String module, String packageName, String sha256, int loc, boolean test,
                      String parseError) {
}
