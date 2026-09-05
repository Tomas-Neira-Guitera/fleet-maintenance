package org.example.dto;

import java.util.List;

/** Wrapper {"items": [...]} usado por los listados sin paginar de este dominio. */
public record ListResponse<T>(List<T> items) {
}
