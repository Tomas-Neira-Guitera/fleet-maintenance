package org.example.dto;

import java.util.List;

/** Wrapper paginado usado por GET /api/vehicles?view=fleet-status. */
public record PagedResponse<T>(int page, int pageSize, long total, List<T> items) {
}
