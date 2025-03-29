package com.jblazing.matchmaking;

import com.jblazing.game.service.GameService;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

public class FifoMatchMaking extends AbstractMatchMakingService{

    private final Queue<UUID> queue;
    private final GameService gameService;
    private final Random rand;

    public FifoMatchMaking(GameService gameService) {
        this.queue = new ConcurrentLinkedDeque<>();
        this.gameService = gameService;
        this.rand = new Random();
    }

    @Override
    public boolean joinQueue(UUID playerId) {
        return queue.add(playerId);
    }

    @Override
    public boolean leaveQueue(UUID playerId) {
        return queue.remove(playerId);
    }

    @Override
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    @Scheduled(fixedRate = 2000 )
    protected void createGames() {

        if(queue.size() < 2){
            return;
        }

        while(queue.size() > 1){
            UUID firstPlayer = queue.remove();
            UUID secondPlayer = queue.remove();
            List<String> players = List.of(firstPlayer.toString(), secondPlayer.toString());
            gameService.createNewGame(UUID.randomUUID( ), players , players.get(rand.nextInt(2)));
        }

    }
}
