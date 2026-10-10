package com.jblazing.matchmaking;

import com.jblazing.game.service.GameService;
import com.jblazing.player.Player;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;

public class FifoMatchMaking extends AbstractMatchMakingService{

    private final Queue<Player> queue;
    private final GameService gameService;
    private final Random rand;

    public FifoMatchMaking(GameService gameService) {
        this.queue = new ConcurrentLinkedDeque<>();
        this.gameService = gameService;
        this.rand = new Random();
    }

    @Override
    public boolean joinQueue(Player player) {
        return !queue.contains(player) && queue.add(player);
    }

    @Override
    public boolean leaveQueue(Player player) {
        return queue.remove(player);
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
            Player firstPlayer = queue.remove();
            Player secondPlayer = queue.remove();
            // random colours
            if (rand.nextBoolean()) {
                gameService.createNewGame(firstPlayer, secondPlayer);
            } else {
                gameService.createNewGame(secondPlayer, firstPlayer);
            }
        }

    }
}
