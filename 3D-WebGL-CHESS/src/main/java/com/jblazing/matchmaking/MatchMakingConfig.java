package com.jblazing.matchmaking;

import com.jblazing.game.service.GameService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchMakingConfig {


    @Bean
    public MatchMakingService matchMakingService(GameService gameService) {
        return new FifoMatchMaking(gameService);
    }

}
