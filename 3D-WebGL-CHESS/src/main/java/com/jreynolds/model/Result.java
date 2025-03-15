package com.jreynolds.model;

import java.util.List;
import java.util.Map;

public record Result(Map<String,String> moveResult, String boardState, List<Map<String,String>> moveHistory) {
}
