package com.codelens.service;

import com.codelens.domain.RunMode;

import java.util.Set;

// reset = everything was rebuilt; otherwise only the listed paths changed
public record AnalysisCompletedEvent(long projectId, long runId, RunMode mode, boolean reset,
                                     Set<String> changedPaths, Set<String> deletedPaths) {
}
