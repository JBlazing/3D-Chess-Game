package com.jblazing.matchmaking;

import com.jblazing.game.service.GameService;
import com.jblazing.user.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling   // runs EloMatchMaking's pairing pass
public class MatchMakingConfig {


    @Bean
    public MatchMakingService matchMakingService(GameService gameService, UserAccountRepository userAccountRepository) {
        return new EloMatchMaking(gameService, userAccountRepository);
    }

}
