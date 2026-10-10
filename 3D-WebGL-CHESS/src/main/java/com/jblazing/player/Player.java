package com.jblazing.player;

/**
 * Someone taking part in a game.
 *
 * @param id        stable key used in game state: "user:&lt;accountId&gt;" for signed-in users,
 *                  "guest:&lt;uuid&gt;" for a browser session when sign-in is disabled, or "COMPUTER"
 * @param accountId the user's account, or null for guests and the computer
 */
public record Player(String id, Long accountId) {

    public static final Player COMPUTER = new Player("COMPUTER", null);

    public static Player account(long accountId) {
        return new Player("user:" + accountId, accountId);
    }

    public static Player guest(String sessionKey) {
        return new Player("guest:" + sessionKey, null);
    }

    /** Rebuilds a player from what a game record stores. */
    public static Player of(String id, Long accountId) {
        return COMPUTER.id().equals(id) ? COMPUTER : new Player(id, accountId);
    }

    public boolean isComputer() {
        return COMPUTER.id().equals(id);
    }
}
