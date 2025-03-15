function evalMove(playerMove, currentBoardState) {

    const game  = (currentBoardState) ? new Chess(currentBoardState) : new Chess();

    if (playerMove == null) {
        let moves = game.moves({verbose: true});
        let p = Math.floor(Math.random() * (moves.length + 1))
        move = moves[p];
    } else {
        move = {from: playerMove.from, to: playerMove.to}
    }

    const result = game.move(move);
    const newBoardState = game.fen();
    const history = game.history({verbose: true});
    const resultClass = Java.type("com.jreynolds.model.Result")
    return new resultClass(result, newBoardState, history);
}
