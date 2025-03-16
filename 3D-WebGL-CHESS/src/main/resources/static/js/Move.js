function evalMove(playerMove, currentBoardState) {

    const game  = (currentBoardState) ? new Chess(currentBoardState) : new Chess();

    let move;
    if(playerMove) {
        move = {"from": playerMove.from(), "to": playerMove.to()};
    } else{
        let moves = game.moves({verbose: true});
        let p = Math.floor(Math.random() * (moves.length+1))
        move = moves[p];
        playerMove = new (Java.type("com.jreynolds.model.Move"))(move.from,move.to)
    }
    log.info(playerMove.toString())
    log.info(JSON.stringify(move));
    log.info(JSON.stringify(game));
    const result = game.move(move);
    log.info(JSON.stringify(result));
    const newBoardState = game.fen();
    const history = game.history({verbose: true});
    const resultClass = Java.type("com.jreynolds.model.Result")

    return (playerMove) ? new resultClass(playerMove, result, newBoardState, history[0]) : null;
}
