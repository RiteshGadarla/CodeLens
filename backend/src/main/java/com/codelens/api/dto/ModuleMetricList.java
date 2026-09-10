package com.codelens.api.dto;

import com.codelens.metrics.ModuleMetrics;

import java.util.List;

public record ModuleMetricList(List<ModuleMetrics> items) {
}
