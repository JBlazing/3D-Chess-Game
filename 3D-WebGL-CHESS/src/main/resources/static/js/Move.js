function evalMove(playerMove, currentBoardState) {
    import {Chess} from 'classpath:/static/js/Chess.js'

    const chess = (currentBoardState) ? new Chess(currentBoardState) : new Chess();

    let move;
    if (playerMove == null) {
        let moves = chess.moves({verbose: true});
        let p = Math.floor(Math.random() * (moves.length + 1))
        move = moves[p];
    } else {
        move = {"from": playerMove.from(), "to": playerMove.to()}
    }

    const result = chess.move(move);
    const newBoardState = chess.fen();
    const history = chess.history({verbose: true})

    return new Result(result, newBoardState, history);
}
