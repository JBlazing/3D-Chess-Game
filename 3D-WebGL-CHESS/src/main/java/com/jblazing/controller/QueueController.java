package com.jblazing.controller;

import com.jblazing.matchmaking.MatchMakingService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(path = "/queue")
@AllArgsConstructor
public class QueueController {


    MatchMakingService matchMakingService;


    @PutMapping("join")
    public boolean joinQueue(UUID playerId){
        return matchMakingService.joinQueue(playerId);
    }

    @DeleteMapping("leave")
    public boolean leaveQueue(UUID playerId){
        return matchMakingService.leaveQueue(playerId);
    }



}
