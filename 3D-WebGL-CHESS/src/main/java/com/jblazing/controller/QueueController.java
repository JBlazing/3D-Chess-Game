package com.jblazing.controller;

import com.jblazing.matchmaking.MatchMakingService;
import com.jblazing.player.Player;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/queue")
@AllArgsConstructor
public class QueueController {


    MatchMakingService matchMakingService;


    @PutMapping("join")
    public boolean joinQueue(Player player){
        return matchMakingService.joinQueue(player);
    }

    @DeleteMapping("leave")
    public boolean leaveQueue(Player player){
        return matchMakingService.leaveQueue(player);
    }



}
