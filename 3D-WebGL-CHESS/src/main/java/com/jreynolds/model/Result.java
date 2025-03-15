package com.jreynolds.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record Result(Move move, Map moveResult, String boardState, Map moveHistory) {
}
