package com.jreynolds.model;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
public class GameState {

    private UUID gameId;

    private String boardState;

    private List<Map<String,String>> gameHistory;

}
