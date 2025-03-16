package com.jreynolds.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GameState(UUID gameId,
                        List<String> playersId,
                        String currentPlayersTurn,
                        String boardState,
                        List<Map> gameHistory) {

}
