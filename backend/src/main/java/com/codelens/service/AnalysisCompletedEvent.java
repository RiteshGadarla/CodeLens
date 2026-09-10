package com.codelens.service;

import com.codelens.domain.RunMode;

public record AnalysisCompletedEvent(long projectId, long runId, RunMode mode) {
}
