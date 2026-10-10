package com.jblazing.controller;

import com.jblazing.matchmaking.MatchMakingService;
import com.jblazing.matchmaking.QueueStatus;
import com.jblazing.player.Player;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Matchmaking for online games. The page polls {@code status} while searching, which also keeps its place. */
@RestController
@RequestMapping(path = "/queue")
@AllArgsConstructor
public class QueueController {


    MatchMakingService matchMakingService;


    @PutMapping("join")
    public QueueStatus joinQueue(Player player){
        return matchMakingService.joinQueue(player);
    }

    @DeleteMapping("leave")
    public boolean leaveQueue(Player player){
        return matchMakingService.leaveQueue(player);
    }

    @GetMapping("status")
    public QueueStatus status(Player player){
        return matchMakingService.status(player);
    }



}
