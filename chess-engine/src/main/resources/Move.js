function evalMove(playerMove, currentBoardState) {

    const game  = (currentBoardState) ? new Chess(currentBoardState) : new Chess();

    let move;
    if(playerMove) {
        // chess.js only matches a promotion move when the piece is given; the field is ignored otherwise
        move = {"from": playerMove.from(), "to": playerMove.to(), "promotion": playerMove.promotion() || "q"};
    } else{
        // chess.js lists a promotion once per piece; the computer always promotes to a queen
        let moves = game.moves({verbose: true}).filter(m => !m.promotion || m.promotion === "q");
        if(moves.length === 0) {
            return null;
        }
        let p = Math.floor(Math.random() * moves.length)
        move = moves[p];
        playerMove = new (Java.type("com.jblazing.model.Move"))(move.from, move.to, move.promotion || null)
    }
    const result = game.move(move);
    const newBoardState = game.fen();
    const history = game.history({verbose: true});
    const resultClass = Java.type("com.jblazing.model.Result")
    const status = game.in_checkmate() ? "CHECKMATE"
        : game.in_stalemate() ? "STALEMATE"
        : game.in_draw() ? "DRAW"
        : "IN_PROGRESS";

    return (result) ? new resultClass(playerMove, result, newBoardState, history[0], status) : null;
}
