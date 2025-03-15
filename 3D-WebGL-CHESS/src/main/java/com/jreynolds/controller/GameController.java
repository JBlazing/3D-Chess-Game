package com.jreynolds.controller;


import com.jreynolds.game.service.GameService;
import com.jreynolds.model.Move;
import com.jreynolds.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(path = "/game")
public class GameController {


    @Autowired
    GameService gameService;

    @GetMapping("start")
    public String startGame(){
       return UUID.randomUUID().toString();
    }

    @PostMapping("{gameId}/move")
    public Result move(@PathVariable("gameId") UUID gameId, @RequestBody Move move){
        return gameService.move(gameId,move);
    }

    @GetMapping("{gameId}/opponents/move")
    public Result move(@PathVariable("gameId") UUID gameId){
        return gameService.move(gameId,null);
    }


}
