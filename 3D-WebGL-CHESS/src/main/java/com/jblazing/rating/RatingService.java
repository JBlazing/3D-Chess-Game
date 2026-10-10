package com.jblazing.rating;

import com.jblazing.game.persistence.GameRecord;
import com.jblazing.game.persistence.GameRecordRepository;
import com.jblazing.user.UserAccount;
import com.jblazing.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elo ratings for games between two signed-in players.
 *
 * <p>Expected score E = 1 / (1 + 10^((opponent - player) / 400)); after a game the rating moves by
 * K * (score - E), with score 1 for a win, 0.5 for a draw and 0 for a loss. K follows FIDE: 40 for a
 * player's first 30 rated games so new players reach their level quickly, 10 from 2400, else 20.
 */
@Service
public class RatingService {
    private static final Logger logger = LoggerFactory.getLogger(RatingService.class);

    public static final int STARTING_RATING = 1200;
    static final int PROVISIONAL_GAMES = 30;

    private final GameRecordRepository gameRecordRepository;
    private final UserAccountRepository userAccountRepository;

    public RatingService(GameRecordRepository gameRecordRepository, UserAccountRepository userAccountRepository) {
        this.gameRecordRepository = gameRecordRepository;
        this.userAccountRepository = userAccountRepository;
    }

    static double expectedScore(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10, (opponentRating - rating) / 400.0));
    }

    static int kFactor(int rating, int ratedGames) {
        if (ratedGames < PROVISIONAL_GAMES) return 40;
        return rating >= 2400 ? 10 : 20;
    }

    /** Rating change for a player scoring {@code score} (1, 0.5 or 0) against the opponent. */
    static int change(int rating, int ratedGames, int opponentRating, double score) {
        return (int) Math.round(kFactor(rating, ratedGames) * (score - expectedScore(rating, opponentRating)));
    }

    /**
     * Updates both players' ratings for a finished rated game. Safe to call more than once: the
     * game row records the change, and a game that already has one is left alone.
     */
    @Transactional
    public void applyResult(String gameId) {
        GameRecord game = gameRecordRepository.findById(gameId).orElseThrow();
        if (!game.isRated() || game.getWhiteRatingChange() != null || "IN_PROGRESS".equals(game.getStatus())) {
            return;
        }
        // lock in id order so two games finishing at once can't deadlock on the same pair
        long whiteId = game.getWhiteAccount().getId(), blackId = game.getBlackAccount().getId();
        UserAccount first = userAccountRepository.findForUpdate(Math.min(whiteId, blackId)).orElseThrow();
        UserAccount second = userAccountRepository.findForUpdate(Math.max(whiteId, blackId)).orElseThrow();
        UserAccount white = first.getId() == whiteId ? first : second;
        UserAccount black = first.getId() == whiteId ? second : first;

        double whiteScore = "WHITE".equals(game.getWinner()) ? 1 : "BLACK".equals(game.getWinner()) ? 0 : 0.5;
        int whiteChange = change(white.getRating(), white.getRatedGames(), black.getRating(), whiteScore);
        int blackChange = change(black.getRating(), black.getRatedGames(), white.getRating(), 1 - whiteScore);

        game.setWhiteRatingChange(whiteChange);
        game.setBlackRatingChange(blackChange);
        white.setRating(white.getRating() + whiteChange);
        black.setRating(black.getRating() + blackChange);
        white.setRatedGames(white.getRatedGames() + 1);
        black.setRatedGames(black.getRatedGames() + 1);
        logger.info("Rated game {} ({}): white {} {}{}, black {} {}{}", gameId, game.getStatus(),
                white.getId(), whiteChange >= 0 ? "+" : "", whiteChange, black.getId(), blackChange >= 0 ? "+" : "", blackChange);
    }
}
