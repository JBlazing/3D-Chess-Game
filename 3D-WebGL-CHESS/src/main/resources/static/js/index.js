/*
	The 3D game page (index.html): the panel, live and online play, replays, and starting the scene.
	Loaded at the end of <body>, after the page's elements, so its top-level code can use them.
*/

/*

	The project is seperated into many javascript files for clean code
	
	The technqiues used are described in this section
	
	for Animation we used b-slpine interpolation for animations 
		this is all located in the Animation.js file
		
	for Textures all of the texture loading and processing in Textures.js
	
	Many of the other techinques where reused from previous projects
	
	All of the rendering happens in the draw function in the Scene.js file
	
	
*/






var gl = null;     // WebGL context
var shaderProgram = null;    // The program (shaders)
var materials = null;
var skyPrg = null;
var anime = [];
var Pos = null;
var boxPos = null;
var chess = null;

var curMove = 0;
var allMoves = null;
var gameId = null;           // set once the server has created the game
var playerId = "";
var moveLock = false;
var playerColor = "w";       // the side you play: "w" or "b"
var sideChoice = "white";    // what was picked: "white", "black" or "random"
var viewingAsBlack = false;  // whether the camera has been turned to black's side
var startCounter = 0;        // ignores the reply to a start request that a newer one replaced

/* ---------- HUD ---------- */

var thinking = false;      // waiting on the server for our move + the computer's reply
var gameOverShown = false;

const PIECE_NAMES = { p: "Pawn", n: "Knight", b: "Bishop", r: "Rook", q: "Queen", k: "King" };
const GLYPHS = {
    w: { k: "♔", q: "♕", r: "♖", b: "♗", n: "♘", p: "♙" },
    b: { k: "♚", q: "♛", r: "♜", b: "♝", n: "♞", p: "♟" }
};

function $id(id){ return document.getElementById(id); }

/*
    chess.js lists a promotion once per piece (=Q, =R, =B, =N) with identical squares,
    so cycling could silently pick a rook; pawns always promote to a queen
*/
function playerMoves(){
    return chess.moves({verbose : true}).filter(m => !m.promotion || m.promotion === "q");
}

function canPlay(){
    return !replay && gameId && !thinking && !chess.game_over() && chess.turn() === playerColor && allMoves && allMoves.length > 0;
}

function highlightSelected(){
    if(!allMoves || allMoves.length === 0) return;
    const mv = allMoves[curMove];
    try{
        clearMoves();
        showMove(mv.from, mv.to);
    }
    catch(err){
        // models are still loading; the highlight shows up on the next selection
    }
}

function selectMove(delta){
    if(!canPlay()) return;
    curMove = (curMove + delta + allMoves.length) % allMoves.length;
    highlightSelected();
    refreshHud();
}

function playSelected(){
    if(!canPlay()) return;
    const mv = allMoves[curMove];
    move(mv.from, mv.to, mv.promotion);
}

/* ---------- Clicking pieces ---------- */

var selectedFrom = null;   // square of the piece picked by clicking, waiting for a destination

function isOwnPiece(square){
    const piece = square && chess.get(square);
    return !!piece && piece.color === chess.turn();
}

function clickTarget(square){
    if(!square || !canPlay()) return null;
    if(selectedFrom && allMoves.some(m => m.from === selectedFrom && m.to === square)) return "destination";
    if(isOwnPiece(square)) return "piece";
    return null;
}

function clearSelection(){
    selectedFrom = null;
    clearMoves();
}

function onSquareClicked(square){
    const target = clickTarget(square);
    if(target === "destination"){
        const mv = allMoves.find(m => m.from === selectedFrom && m.to === square);
        clearSelection();
        move(mv.from, mv.to, mv.promotion);
        return;
    }
    if(target === "piece"){
        clearMoves();
        selectedFrom = square;
        const piece = pieceAt(square);
        if(piece) piece.Selected = true;
        showMoves(square);
        // keep the panel's move in step with the clicked piece
        const first = allMoves.findIndex(m => m.from === square);
        if(first >= 0) curMove = first;
        refreshHud();
        return;
    }
    if(selectedFrom){
        clearSelection();
        refreshHud();
    }
}

function onSquareHovered(square){
    document.getElementById("canvas").style.cursor = clickTarget(square) ? "pointer" : "";
}

/* ---------- Choosing a side ---------- */

const SIDE_KEY = "chess.side";

function storedSide(){
    try { return localStorage.getItem(SIDE_KEY); } catch(e){ return null; }
}

function rememberSide(choice){
    try { localStorage.setItem(SIDE_KEY, choice); } catch(e){}
}

function validSide(choice){
    return ["white", "black", "random"].includes(choice) ? choice : "white";
}

// set while no game has been started (on arrival, or after cancelling a search with nothing
// behind it); the New game dialog can still be closed, and the board then waits for a choice
var awaitingChoice = false;

/** required: there is no game to go back to, so the board waits for one to be picked. */
function openSideChooser(required){
    if(required) awaitingChoice = true;
    document.querySelectorAll(".side-option").forEach(b => b.classList.toggle("current", b.dataset.side === sideChoice));
    $id("chooserWarning").hidden = !onlineInProgress();
    updateOnlineSub();
    $id("gameOver").hidden = true;
    $id("sideChooser").hidden = false;
    refreshHud();
    (document.querySelector(".side-option.current") || document.querySelector(".side-option")).focus();
}

function updateOnlineSub(){
    $id("onlineSub").textContent = myRating != null
        ? "Rated · your rating " + myRating + " · matched by rating"
        : "Unrated · sign in to play rated games";
}

/** Closes the dialog; force is for starting a game, which ends the wait for a choice. */
function closeSideChooser(force){
    if(force) awaitingChoice = false;
    $id("sideChooser").hidden = true;
}

// hides the result so the final position can be looked at; gameOverShown stays set so it doesn't reopen
function closeGameOver(){
    $id("gameOver").hidden = true;
}

// while a dialog is open, keys belong to it: stop the board's handlers (CameraInteractor, replay)
// from acting on them, without preventing the dialog's buttons from responding to Enter/Space
function dialogOpen(){
    return !$id("sideChooser").hidden || !$id("gameOver").hidden || !$id("searching").hidden;
}
window.addEventListener("keydown", e => {
    if(!dialogOpen()) return;
    if(e.key === "Escape"){
        if(!$id("searching").hidden) cancelSearch();
        else if(!$id("sideChooser").hidden) closeSideChooser();
        else closeGameOver();
    }
    e.stopImmediatePropagation();
}, true);
document.addEventListener("click", e => {
    if(e.target === $id("sideChooser")) closeSideChooser();   // a click on the backdrop
});

/** Starts a new game as "white", "black" or "random"; from a replay this goes to the game page. */
async function newGame(choice){
    choice = validSide(choice);
    rememberSide(choice);
    if(replay){
        window.location.href = "/index?side=" + choice;
        return;
    }
    await resignOnlineGame();
    startGame(choice);
}

/** Names the two sides from your point of view: you, and the opponent. */
function applySideLabels(color, opponent, you){
    const youAreWhite = color === "w";
    you = you || "You";
    $id("playerWhiteName").textContent = youAreWhite ? you : opponent;
    $id("playerBlackName").textContent = youAreWhite ? opponent : you;
    $id("capturedByWhiteLabel").textContent = youAreWhite ? "You" : "Rival";
    $id("capturedByBlackLabel").textContent = youAreWhite ? "Rival" : "You";
    // opponent on top, you at the bottom, the same way round as the board
    $id("playerWhite").style.order = youAreWhite ? 2 : 1;
    $id("playerBlack").style.order = youAreWhite ? 1 : 2;
    $id("capturedByWhite").parentElement.style.order = youAreWhite ? 2 : 1;
    $id("capturedByBlack").parentElement.style.order = youAreWhite ? 1 : 2;
}

/** Turns the camera so your pieces are nearest you. */
function faceSide(color){
    const black = color === "b";
    if(black !== viewingAsBlack){
        Scene.camera.changeAzimuth(180);
        viewingAsBlack = black;
    }
}

function setStatus(text, kind){
    $id("statusText").textContent = text;
    $id("status").className = "status" + (kind ? " " + kind : "");
}

function refreshHud(){
    if(!chess) return;
    const turn = chess.turn();
    $id("playerWhite").classList.toggle("active", turn === "w" && !chess.game_over());
    $id("playerBlack").classList.toggle("active", turn === "b" && !chess.game_over());

    if(replay){
        refreshReplay();
    }
    else{
        if(chess.game_over() || gameOverShown) setStatus("Game over");
        else if(!gameId && awaitingChoice) setStatus("Choose a game to start");
        else if(!gameId) setStatus("Starting game…");
        else if(thinking) setStatus("Sending your move…", "thinking");
        else if(chess.in_check() && turn === playerColor) setStatus("Check — your move", "check");
        else if(turn === playerColor) setStatus("Your move");
        else setStatus(online ? "Waiting for " + online.opponent.name + "…" : "Computer is thinking…", "thinking");

        // Selected move
        const playable = canPlay();
        if(playable && curMove >= allMoves.length) curMove = 0;
        const mv = playable ? allMoves[curMove] : null;
        $id("selectedSan").textContent = mv ? mv.san : "—";
        $id("selectedDetail").textContent = mv
            ? `${PIECE_NAMES[mv.piece]} ${mv.from}→${mv.to}${mv.promotion ? " = " + PIECE_NAMES[mv.promotion] : ""} · ${curMove + 1}/${allMoves.length}`
            : "";
        ["prevMove", "playMove", "nextMove"].forEach(id => $id(id).disabled = !playable);
    }

    // resigning is offered while a game is being played
    $id("resignBtn").hidden = !!replay || !gameId || gameOverShown || chess.game_over();

    // a live game lists the moves played; a replay lists the whole game with the current move marked
    if(replay) renderMoveList(replay.sans, replay.ply, true);
    else renderMoveList(chess.history(), chess.history().length, false);

    const verbose = chess.history({ verbose: true });

    // Captured pieces, grouped by who took them
    const taken = { w: [], b: [] };
    verbose.filter(m => m.captured).forEach(m => {
        taken[m.color].push(GLYPHS[m.color === "w" ? "b" : "w"][m.captured]);
    });
    [["capturedByWhite", "w"], ["capturedByBlack", "b"]].forEach(([id, color]) => {
        const el = $id(id);
        el.textContent = taken[color].join("") || "None";
        el.className = taken[color].length ? "captured-pieces" : "captured-none";
    });
}

/** One row per full move. played moves are shown normally, later ones dimmed; replay moves can be clicked. */
function renderMoveList(plies, played, clickable){
    const list = $id("history");
    list.textContent = "";
    let current = null;
    for(let i = 0; i < plies.length; i += 2){
        const li = document.createElement("li");
        const num = document.createElement("span");
        num.className = "num";
        num.textContent = (i / 2 + 1) + ".";
        li.appendChild(num);
        [i, i + 1].forEach(index => {
            const cell = document.createElement(clickable && index < plies.length ? "button" : "span");
            cell.textContent = plies[index] || "";
            if(clickable && index < plies.length){
                cell.type = "button";
                cell.classList.add("ply");
                cell.setAttribute("aria-label", "Go to move " + (Math.floor(index / 2) + 1) + (index % 2 ? " black " : " white ") + plies[index]);
                cell.addEventListener("click", () => { replayPause(); replayGoTo(index + 1); });
            }
            if(index === played - 1){ cell.classList.add("last"); current = li; }
            else if(index >= played) cell.classList.add("future");
            li.appendChild(cell);
        });
        list.appendChild(li);
    }
    $id("historyEmpty").hidden = plies.length > 0;
    if(clickable){
        if(current) current.scrollIntoView({ block: "nearest" });
        else list.scrollTop = 0;
    }
    else{
        list.scrollTop = list.scrollHeight;
    }
}

function showGameOver(){
    // a replay reaching the final position shows the result in the panel instead; an online
    // game's result comes from the server (it knows the rating change), see finishOnline
    if(gameOverShown || replay || online) return;
    gameOverShown = true;
    let title, detail;
    if(chess.in_checkmate()){
        // the side to move is the side that was mated
        const youWon = chess.turn() !== playerColor;
        title = youWon ? "Checkmate — you win" : "Checkmate — you lose";
        detail = youWon ? "Nicely played." : "The computer got you this time.";
    }
    else if(chess.in_stalemate()){ title = "Stalemate"; detail = "No legal moves left — it's a draw."; }
    else if(chess.in_threefold_repetition()){ title = "Draw"; detail = "Threefold repetition."; }
    else if(chess.insufficient_material()){ title = "Draw"; detail = "Not enough material to checkmate."; }
    else { title = "Draw"; detail = "Fifty-move rule."; }
    $id("resultTitle").textContent = title;
    $id("resultDetail").textContent = detail;
    $id("playAgain").textContent = "Play again";
    $id("switchSide").textContent = playerColor === "w" ? "Play as Black" : "Play as White";
    $id("resultRating").hidden = true;
    $id("gameOver").hidden = false;
    refreshHud();
}

// Match the canvas resolution to its on-screen size so the scene stays sharp and undistorted
function resizeCanvas(){
    const c = $id("canvas");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = Math.max(1, Math.round(c.clientWidth * dpr));
    const h = Math.max(1, Math.round(c.clientHeight * dpr));
    if(c.width !== w || c.height !== h){
        c.width = w;
        c.height = h;
    }
    if(gl){
        gl.viewportWidth = w;
        gl.viewportHeight = h;
    }
}

/* ---------- Replay ---------- */

// set when the page was opened as /index?replay=<gameId>: the saved game is replayed instead of playing
var replay = null;            // { game, sans, ply, timer }
var startSnapshot = null;     // every scene object's starting square, position and visibility
const REPLAY_STEP_MS = 1500;  // autoplay pace: one move animation plus a short pause

function replayResultOf(game){
    const how = { CHECKMATE: "by checkmate", RESIGNED: "by resignation", ABANDONED: "by abandonment", TIMEOUT: "on time" }[game.status];
    if(game.status === "ABORTED") return ["open", "Stopped when a new game started"];
    if(how && game.winner) return game.winner === game.color ? ["win", "You won " + how] : ["loss", "You lost " + how];
    if(game.status === "TIMEOUT") return ["draw", "Draw on time"];
    if(game.status === "STALEMATE") return ["draw", "Draw by stalemate"];
    if(game.status === "DRAW") return ["draw", "Draw"];
    return ["open", "Unfinished game"];
}

var boardLoaded = false;

/**
 * Resolves once the models are loaded and every piece knows its starting square. That is only
 * checked until it first holds: later, captured pieces have no square, and the check would never pass.
 */
function boardReady(){
    return new Promise(resolve => {
        (function check(){
            if(boardLoaded || (Pos && Scene.boxs.length === 64 && Scene.objects.filter(o => o.cord).length === 32)){
                boardLoaded = true;
                resolve();
            }
            else setTimeout(check, 50);
        })();
    });
}

function takeSnapshot(){
    return Scene.objects.map(o => ({ obj: o, cord: o.cord, location: o.location.slice(), isVisible: o.isVisible }));
}

/** Back to the starting position: promoted pieces removed, everything else where it began. */
function resetBoard(){
    boardGeneration++;
    Scene.objects = Scene.objects.filter(o => !o.promoted);
    startSnapshot.forEach(s => {
        s.obj.cord = s.cord;
        s.obj.location = s.location.slice();
        s.obj.isVisible = s.isVisible;
        s.obj.Selected = false;
        delete s.obj.animation;
    });
    chess.reset();
    clearMoves();
}

function replayApply(san, instant){
    // chess.js resolves the SAN to a full move (squares, captures, castling, promotion)
    const move = new Chess(chess.fen()).move(san);
    if(!move) throw new Error("Can't replay move " + san);
    createAnime(move, move, instant);
}

/** Shows the position after the first n moves; stepping one move forward animates it. */
function replayGoTo(n, animate){
    if(!replay || !startSnapshot) return;
    n = Math.max(0, Math.min(replay.sans.length, n));
    if(animate && n === replay.ply + 1){
        replayApply(replay.sans[replay.ply], false);
    }
    else{
        resetBoard();
        for(let i = 0; i < n; i++) replayApply(replay.sans[i], true);
    }
    replay.ply = n;
    clearMoves();
    refreshHud();
}

function replayStep(delta){
    replayPause();
    replayGoTo(replay.ply + delta, delta === 1);
}

function replayToggle(){
    if(replay.timer) { replayPause(); return; }
    if(replay.ply >= replay.sans.length) replayGoTo(0);
    replayGoTo(replay.ply + 1, true);
    replay.timer = setInterval(() => {
        if(replay.ply >= replay.sans.length) replayPause();
        else replayGoTo(replay.ply + 1, true);
    }, REPLAY_STEP_MS);
    refreshHud();
}

function replayPause(){
    if(!replay || !replay.timer) return;
    clearInterval(replay.timer);
    replay.timer = null;
    refreshHud();
}

function refreshReplay(){
    const total = replay.sans.length, ply = replay.ply;
    if(!replay.game){
        return;
    }
    setStatus(replay.timer ? "Replaying…" : (ply >= total ? "End of game" : "Paused"), replay.timer ? "thinking" : null);
    $id("replaySan").textContent = ply === 0 ? "Start" : Math.ceil(ply / 2) + (ply % 2 ? ". " : "… ") + replay.sans[ply - 1];
    $id("replayCount").textContent = ply + " / " + total;
    const scrub = $id("replayScrub");
    scrub.max = total;
    scrub.value = ply;
    scrub.style.setProperty("--fill", (total ? ply / total * 100 : 0) + "%");
    $id("replayStart").disabled = $id("replayBack").disabled = ply === 0;
    $id("replayForward").disabled = $id("replayEnd").disabled = ply >= total;
    $id("replayPlay").disabled = total === 0;
    $id("replayPlay").textContent = replay.timer ? "⏸︎ Pause" : (ply >= total && total > 0 ? "↺ Replay" : "▶︎ Play");
    $id("replayPlay").setAttribute("aria-label", replay.timer ? "Pause" : "Play");
}

function enterReplayUi(){
    document.title = "Replay - 3D Chess";
    $id("moveSection").hidden = true;
    $id("replaySection").hidden = false;
    $id("backToHistory").hidden = false;
    $id("playHint").hidden = true;
    $id("replayHint").hidden = false;
    setStatus("Loading game…");
    $id("replayScrub").addEventListener("input", e => { replayPause(); replayGoTo(Number(e.target.value)); });
    // replay keys win over the live-game keys in CameraInteractor (capture phase runs first)
    window.addEventListener("keydown", e => {
        const keys = { 65: () => replayStep(-1), 68: () => replayStep(1), 32: replayToggle,
                       36: () => { replayPause(); replayGoTo(0); }, 35: () => { replayPause(); replayGoTo(replay.sans.length); }, 13: () => {} };
        // let a focused button or the scrubber handle its own keys
        const tag = e.target.tagName;
        const native = (tag === "BUTTON" && [13, 32].includes(e.keyCode)) || (tag === "INPUT" && [13, 32, 35, 36].includes(e.keyCode));
        if(!replay.game || !keys[e.keyCode] || native) return;
        e.preventDefault();
        // immediate: CameraInteractor's handler is on window too, and would highlight live-game moves
        e.stopImmediatePropagation();
        keys[e.keyCode]();
    }, true);
}

async function startReplay(id){
    try{
        const game = await fetch(`/game/${encodeURIComponent(id)}`).then(checkAuth).then(readResult);
        replay.game = game;
        replay.sans = game.moves ? game.moves.split(" ") : [];

        const color = game.color === "WHITE" ? "w" : "b";
        applySideLabels(color, game.opponent === "COMPUTER" ? "Computer" : "Opponent");
        const [kind, text] = replayResultOf(game);
        $id("replayResult").textContent = text;
        $id("replayResult").className = "replay-result " + kind;

        await boardReady();
        startSnapshot = takeSnapshot();
        // view the game from the side you played
        faceSide(color);
        replayGoTo(0);
    }
    catch(err){
        console.log(err);
        setStatus(err.message && !err.message.startsWith("Not logged") ? err.message : "Couldn't load this game.", "check");
    }
}

/* ---------- Online play ---------- */

var online = null;        // the online game being played: { gameId, rated, you, opponent, over }
var search = null;        // matchmaking in progress: { startedAt, pollTimer, clockTimer }
var myRating = null;      // the signed-in player's rating; null for guests
var lastResultOnline = false;
const ONLINE_POLL_MS = 1000;
const RECONNECT_MS = 3000;

function onlineInProgress(){
    return !!online && !online.over;
}

function csrfHeaders(){
    return { 'X-XSRF-TOKEN': csrfToken() };
}

/** "Alice · 1250", or "You · 1200" for your own side; guests and the computer have no rating. */
function sideLabel(side, isYou){
    const name = isYou ? "You" : side.name;
    return side.rating != null ? name + " · " + side.rating : name;
}

/**
 * Joins matchmaking and waits for an opponent (the queue is polled, which also keeps our place).
 * From an online game still being played this only resigns it: the result card then offers to
 * find another opponent, rather than queueing straight away.
 */
async function findOpponent(){
    closeSideChooser(true);
    $id("gameOver").hidden = true;
    if(onlineInProgress()){
        const forGame = online.gameId;
        try{
            const state = await fetch(`/game/${forGame}/resign`, { method: "POST", headers: csrfHeaders() }).then(checkAuth).then(readResult);
            if(online && online.gameId === forGame) finishOnline(state);
        }
        catch(err){
            console.log(err);
            setStatus(err.message || "Couldn't resign.", "check");
        }
        return;
    }
    leaveOnline();
    startCounter++;                // drop any computer game still starting
    stopSearch();
    search = { startedAt: Date.now(), pollTimer: null, clockTimer: setInterval(updateSearchClock, 500) };
    $id("searchDetail").textContent = "Joining the queue…";
    updateSearchClock();
    $id("searching").hidden = false;
    try{
        const status = await fetch("/queue/join", { method: "PUT", headers: csrfHeaders() }).then(checkAuth).then(readResult);
        if(!search) return;
        showQueueStatus(status);
        search.pollTimer = setInterval(pollQueue, ONLINE_POLL_MS);
    }
    catch(err){
        console.log(err);
        $id("searchDetail").textContent = "Couldn't join the queue. Try again.";
    }
}

function updateSearchClock(){
    if(!search) return;
    const s = Math.floor((Date.now() - search.startedAt) / 1000);
    $id("searchTime").textContent = Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
}

function showQueueStatus(status){
    if(status.state === "MATCHED"){
        stopSearch();
        startOnline(status.gameId);
    }
    else if(status.state === "SEARCHING"){
        $id("searchDetail").textContent = status.rating != null
            ? "Your rating " + status.rating + " · looking for " + (status.rating - status.range) + "–" + (status.rating + status.range)
            : "Unrated · looking for another guest";
    }
}

async function pollQueue(){
    if(!search) return;
    try{
        const status = await fetch("/queue/status").then(checkAuth).then(readResult);
        if(!search) return;
        // dropped from the queue (e.g. the tab was asleep): join again
        if(status.state === "IDLE"){
            showQueueStatus(await fetch("/queue/join", { method: "PUT", headers: csrfHeaders() }).then(checkAuth).then(readResult));
        }
        else{
            showQueueStatus(status);
        }
    }
    catch(err){ console.log(err); }
}

function stopSearch(){
    if(!search) return;
    clearInterval(search.pollTimer);
    clearInterval(search.clockTimer);
    search = null;
    $id("searching").hidden = true;
}

async function cancelSearch(){
    if(!search) return;
    stopSearch();
    try{
        await fetch("/queue/leave", { method: "DELETE", headers: csrfHeaders() });
        // a match may have been made just before we left: play it rather than strand the opponent
        const status = await fetch("/queue/status").then(readResult);
        if(status.state === "MATCHED") startOnline(status.gameId);
        // nothing to go back to: choose again
        else if(!gameId && !online) openSideChooser(true);
    }
    catch(err){ console.log(err); }
}

async function startOnline(id){
    try{
        resumeOnline(await fetch(`/game/${encodeURIComponent(id)}/state`).then(checkAuth).then(readResult));
    }
    catch(err){
        console.log(err);
        setStatus("Couldn't open the online game.", "check");
    }
}

/** Shows an online game (just matched, or picked up again after a reload) and starts following it. */
async function resumeOnline(state){
    const token = ++startCounter;
    leaveOnline();
    const youAreWhite = state.color === "WHITE";
    const you = youAreWhite ? state.white : state.black;
    const opponent = youAreWhite ? state.black : state.white;
    online = { gameId: state.gameId, rated: state.rated, you: you, opponent: opponent, over: false };
    playerColor = youAreWhite ? "w" : "b";
    gameId = null;                 // no moves until the position is on the board
    thinking = false;
    gameOverShown = false;
    selectedFrom = null;
    curMove = 0;
    $id("gameOver").hidden = true;
    closeSideChooser(true);
    applySideLabels(playerColor, sideLabel(opponent, false), sideLabel(you, true));
    setClock(state.clock);
    refreshHud();

    await boardReady();
    if(token !== startCounter) return;
    if(!startSnapshot) startSnapshot = takeSnapshot();
    else resetBoard();
    faceSide(playerColor);
    state.moves.forEach(san => replayApply(san, true));
    gameId = state.gameId;
    allMoves = playerMoves();
    refreshHud();
    if(state.status !== "IN_PROGRESS"){
        finishOnline(state);
        return;
    }
    followGame(state.gameId);
}

/* ---------- Live updates ---------- */

var live = null;   // the event stream of the game being played, online or against the computer: { gameId, events, latest, over }

/**
 * Follows a game through the server's event stream: its whole state whenever it changes (moves,
 * the end of the game), and the clock every second. Nothing is polled.
 */
function followGame(id){
    stopFollowing();
    live = { gameId: id, events: null, latest: null, over: false };
    openStream(live);
}

function openStream(following){
    const events = new EventSource(`/game/${encodeURIComponent(following.gameId)}/events`);
    following.events = events;
    const current = () => live === following && following.events === events && !following.over;
    events.addEventListener("state", e => { if(current()) onGameState(JSON.parse(e.data)); });
    events.addEventListener("clock", e => { if(current()) setClock(JSON.parse(e.data)); });
    events.onerror = () => {
        // a dropped connection is reopened by the browser; one the server refused (e.g. signed out) is not
        if(events.readyState !== EventSource.CLOSED || !current()) return;
        setTimeout(() => reconnect(following, events), RECONNECT_MS);
    };
}

/** Opens the stream again after the server refused it, checking first that we're still signed in and in the game. */
async function reconnect(following, events){
    const current = () => live === following && following.events === events && !following.over;
    if(!current()) return;
    try{
        onGameState(await fetch(`/game/${following.gameId}/state`).then(checkAuth).then(readResult));
        if(current()) openStream(following);
    }
    catch(err){
        console.log(err);
        setTimeout(() => reconnect(following, events), RECONNECT_MS);
    }
}

/** Stops following the game; the clocks keep showing their last reading. */
function endFollowing(){
    if(!live) return;
    live.over = true;
    if(live.events) live.events.close();
}

/** Stops following the game and hides the clocks, e.g. when another game starts. */
function stopFollowing(){
    endFollowing();
    live = null;
    setClock(null);
}

/** Shows anything the server pushed while our own move (or the computer's reply) was on its way. */
function catchUp(){
    if(live && live.latest) onGameState(live.latest);
}

/**
 * Shows the game as the server sent it: the clock, moves we haven't seen, the end of the game.
 * While our move is on its way the moves wait; postMove applies it, then catches up.
 */
function onGameState(state){
    if(!live || live.over || state.gameId !== live.gameId) return;
    live.latest = state;
    setClock(state.clock);
    if(thinking) return;
    const have = chess.history().length;
    if(state.moves.length > have){
        // catch up instantly on anything missed, and animate the latest move
        for(let i = have; i < state.moves.length; i++) replayApply(state.moves[i], i < state.moves.length - 1);
        allMoves = playerMoves();
        curMove = 0;
        selectedFrom = null;
    }
    if(online){
        $id("claimBox").hidden = !state.canClaimWin;
        if(state.canClaimWin){
            $id("claimText").textContent = online.opponent.name + " hasn't moved for " + Math.floor(state.opponentIdleSeconds / 60) + " minutes.";
        }
    }
    if(state.status !== "IN_PROGRESS"){
        endFollowing();
        if(online) finishOnline(state);
        else finishComputerGame(state);
    }
    else refreshHud();
}

/**
 * A game against the computer ended on the server. Checkmate, stalemate and draws are shown when
 * the final move lands, and resigning shows its own card; only running out of time is news here.
 */
function finishComputerGame(state){
    if(state.status !== "TIMEOUT" || gameOverShown) return;
    gameOverShown = true;
    const [title, detail] = !state.winner ? ["Draw on time", "A clock ran out, but the other side couldn't have checkmated."]
        : state.winner === state.color ? ["You win on time", "The computer ran out of time."]
        : ["Out of time", "Your clock ran out. The computer wins."];
    showResultCard(title, detail, null, false);
    refreshHud();
}


/* ---------- Blitz clock ---------- */

// The server keeps the clock and pushes it every second and with every change; in between, the
// running side is counted down here for display only, from the last reading.
var clockReading = null;   // { clock, at }: the server's latest clock, and when it arrived (performance.now())
var clockTimer = null;
const LOW_TIME_MS = 20000;

/** Shows the server's clock reading; null hides the clocks (untimed games). */
function setClock(clock){
    clockReading = clock ? { clock: clock, at: performance.now() } : null;
    $id("clockWhite").hidden = $id("clockBlack").hidden = $id("timeControl").hidden = !clock;
    if(clock) $id("timeControl").textContent = "· Blitz " + clock.timeControl;
    const running = !!(clock && clock.running);
    if(running && !clockTimer) clockTimer = setInterval(renderClocks, 100);
    if(!running && clockTimer){ clearInterval(clockTimer); clockTimer = null; }
    renderClocks();
}

function clockMs(side){
    const clock = clockReading.clock;
    const stored = side === "WHITE" ? clock.whiteMs : clock.blackMs;
    return clock.running === side ? Math.max(0, stored - (performance.now() - clockReading.at)) : stored;
}

/** "4:59"; tenths under ten seconds, "0:09.4". */
function formatClock(ms){
    const seconds = Math.floor(ms / 1000);
    const text = Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
    return ms < 10000 ? text + "." + Math.floor(ms / 100) % 10 : text;
}

function renderClocks(){
    if(!clockReading) return;
    [["WHITE", "clockWhite"], ["BLACK", "clockBlack"]].forEach(([side, id]) => {
        const ms = clockMs(side), el = $id(id);
        el.textContent = formatClock(ms);
        el.classList.toggle("running", clockReading.clock.running === side);
        el.classList.toggle("low", ms < LOW_TIME_MS);
    });
}

function finishOnline(state){
    if(!online || online.over) return;
    online.over = true;
    endFollowing();
    setClock(state.clock);
    gameOverShown = true;
    $id("claimBox").hidden = true;
    showOnlineResult(state);
    refreshHud();
    // the badge shows the new rating
    loadUser().then(user => { myRating = user && user.rating != null ? user.rating : null; });
}

function showOnlineResult(state){
    const won = state.winner === state.color;
    const opponent = online.opponent.name;
    const texts = {
        CHECKMATE: won ? ["Checkmate — you win", "You beat " + opponent + "."] : ["Checkmate — you lose", opponent + " got you this time."],
        RESIGNED: won ? ["You win", opponent + " resigned."] : ["You resigned", opponent + " wins."],
        ABANDONED: won ? ["You win", opponent + " left the game."] : ["Game abandoned", "You went too long without moving."],
        TIMEOUT: !state.winner ? ["Draw on time", "A clock ran out, but the other side couldn't have checkmated."]
               : won ? ["You win on time", opponent + " ran out of time."] : ["Out of time", "Your clock ran out. " + opponent + " wins."],
        STALEMATE: ["Stalemate", "No legal moves left — it's a draw."],
        DRAW: ["Draw", "The game is drawn."]
    };
    const [title, detail] = texts[state.status] || ["Game over", ""];
    let rating = null;
    if(state.rated && state.ratingChange != null){
        rating = { before: online.you.rating, change: state.ratingChange };
    }
    showResultCard(title, detail, state.rated ? rating : "unrated", true);
}

/**
 * The result card. rating is { before, change } for a rated game, "unrated" for an online game
 * that wasn't rated, or null (computer games) to leave the line out.
 */
function showResultCard(title, detail, rating, isOnline){
    lastResultOnline = isOnline;
    $id("resultTitle").textContent = title;
    $id("resultDetail").textContent = detail;
    const line = $id("resultRating");
    line.textContent = "";
    if(rating && rating !== "unrated"){
        const after = rating.before + rating.change;
        line.appendChild(document.createTextNode("Rating " + rating.before + " → " + after + " "));
        const delta = document.createElement("span");
        delta.className = rating.change >= 0 ? "up" : "down";
        delta.textContent = "(" + (rating.change >= 0 ? "+" : "") + rating.change + ")";
        line.appendChild(delta);
    }
    else if(rating === "unrated"){
        line.textContent = "Unrated game";
    }
    line.hidden = !rating;
    $id("playAgain").textContent = isOnline ? "Find another opponent" : "Play again";
    $id("switchSide").textContent = isOnline ? "Play the computer" : (playerColor === "w" ? "Play as Black" : "Play as White");
    $id("gameOver").hidden = false;
}

function playAgain(){
    if(lastResultOnline) findOpponent();
    else newGame(sideChoice);
}

function switchSideOrComputer(){
    if(lastResultOnline) openSideChooser();
    else newGame(playerColor === "w" ? "black" : "white");
}

/** Stops following the game being played (online or not), e.g. when another game starts. */
function leaveOnline(){
    stopFollowing();
    online = null;
    $id("claimBox").hidden = true;
}

/** Leaving an online game that is still being played forfeits it. */
async function resignOnlineGame(){
    if(!onlineInProgress()) return;
    try{
        await fetch(`/game/${online.gameId}/resign`, { method: "POST", headers: csrfHeaders() });
    }
    catch(err){ console.log(err); }
}

var resignConfirmTimer = null;

/** Resign button: the first click asks for confirmation, the second resigns. */
async function resign(){
    const button = $id("resignBtn");
    if(!button.classList.contains("confirm")){
        button.classList.add("confirm");
        button.textContent = "Confirm resign";
        resignConfirmTimer = setTimeout(resetResignButton, 4000);
        return;
    }
    resetResignButton();
    const forGame = gameId;
    try{
        const state = await fetch(`/game/${forGame}/resign`, { method: "POST", headers: csrfHeaders() }).then(checkAuth).then(readResult);
        if(gameId !== forGame) return;
        if(online){
            finishOnline(state);
        }
        else{
            gameOverShown = true;
            showResultCard("You resigned", "The computer wins.", null, false);
            refreshHud();
        }
    }
    catch(err){
        console.log(err);
        setStatus(err.message || "Couldn't resign.", "check");
    }
}

function resetResignButton(){
    clearTimeout(resignConfirmTimer);
    const button = $id("resignBtn");
    button.classList.remove("confirm");
    button.textContent = "Resign";
}

async function claimWin(){
    if(!onlineInProgress()) return;
    try{
        finishOnline(await fetch(`/game/${online.gameId}/claim`, { method: "POST", headers: csrfHeaders() }).then(checkAuth).then(readResult));
    }
    catch(err){
        console.log(err);
        setStatus(err.message || "Couldn't claim the win.", "check");
    }
}

/** On load: carry on with an online game in progress, otherwise start a game against the computer. */
/**
 * On arrival: carry on with an online game in progress; start the computer game a link asked for
 * (?side=); otherwise let the player choose what to play.
 */
async function resumeOrStart(side, requested){
    try{
        const re = await fetch("/game/active").then(checkAuth);
        if(re.status === 200){
            resumeOnline(await re.json());
            return;
        }
    }
    catch(err){ console.log(err); }
    if(requested){
        startGame(side);
    }
    else{
        sideChoice = side;   // pre-selects the side picked last time
        openSideChooser(true);
    }
}

/* ---------- Server calls ---------- */

/**
 * Starts a game against the computer. The board is reset in place (no reload); when you play
 * black the server has the computer open, and that move is animated once the models are ready.
 */
async function startGame(choice){
    const token = ++startCounter;
    leaveOnline();
    sideChoice = choice;
    const side = choice === "random" ? (Math.random() < 0.5 ? "white" : "black") : choice;
    playerColor = side === "black" ? "b" : "w";

    gameId = null;
    thinking = false;
    gameOverShown = false;
    selectedFrom = null;
    curMove = 0;
    $id("gameOver").hidden = true;
    closeSideChooser(true);
    applySideLabels(playerColor, "Computer");
    refreshHud();

    // the scene has to be loaded before any move can be shown on it
    await boardReady();
    if(token !== startCounter) return;
    if(!startSnapshot) startSnapshot = takeSnapshot();
    else resetBoard();
    faceSide(playerColor);
    allMoves = playerMoves();
    refreshHud();

    const params = new URLSearchParams({ startingSide: side.toUpperCase() });
    fetch(`/game/start?${params}`)
    .then(checkAuth)
    .then(readResult)
    .then(data => {
        if(token !== startCounter) return;
        gameId = data.gameId;
        playerId = data.playerId;
        allMoves = playerMoves();
        followGame(gameId);   // the computer's moves (its opening, when you play black), the clock, the result
        curMove = 0;
        refreshHud();
    })
    .catch(err => {
        console.log(err);
        if(token === startCounter) setStatus("Couldn't start a game. Try again.", "check");
    });
}

// the server checks every move: it must be this player's game, their turn, and a legal move
function readResult(re){
    if(!re.ok){
        const reason = { 400: "That move was rejected.", 404: "This game is no longer available.", 409: "That move can't be played right now.",
                         503: "The server is busy — try that move again." };
        throw new Error(reason[re.status] || "Something went wrong. Try again.");
    }
    return re.json();
}

async function postMove(move){
    console.log(JSON.stringify(move))
    const forGame = gameId;   // a reply for a game that has since been replaced is dropped
    const path = `/game/${gameId}/move`
    await fetch(path , {
        method: "POST",
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken()
        },
        body: JSON.stringify(move)
    })
    .then(checkAuth)
    .then(readResult)
    .then(re => {
        console.log(re)
        if(forGame !== gameId) return;
        createAnime(re.move, re.moveResult);
        // the reply (the opponent's, or the computer's after it has thought) and the end of the
        // game are pushed by the server; show anything that arrived while this move was on its way
        thinking = false;
        refreshHud();
        catchUp();
    })
    .catch(err => { if(forGame === gameId) moveFailed(err); });
}

function moveFailed(err){
    console.log(err);
    thinking = false;
    refreshHud();
    if(err && err.message && !chess.game_over()) setStatus(err.message, "check");
    // e.g. the move came too late and the game ended on time: the server has pushed the result.
    if(live && live.latest && live.latest.status !== "IN_PROGRESS") onGameState(live.latest);
}

function move(from, to, promotion){
    if(thinking || replay) return;
    thinking = true;
    refreshHud();
    postMove({from: from , to: to, promotion: promotion || null})
}
// cord is the piece's logical square; captured or replaced pieces have it cleared
function pieceAt(square){
    return Scene.objects.filter(obj => obj.cord == square)[0];
}

// instant places the piece without animating, for rebuilding a position in a replay
function animateTo(obj, square, instant){
    obj.cord = square;
    if(instant){
        delete obj.animation;
        obj.location = Pos[obj.name][square].slice();
    }
    else{
        obj.animation = Animation.createAnimation(obj.location , Pos[obj.name][square]);
    }
}

// bumped whenever the board is reset, so callbacks from before the reset can tell
var boardGeneration = 0;

function whenLanded(obj, callback){
    (function check(){
        if(obj.animation) requestAnimFrame(check);
        else callback();
    })();
}

/*
    Every mesh is modelled at its starting square and placed with an offset from
    Pos[meshName], so the promoted piece reuses the GPU buffers of an existing mesh
    of that type and colour (side picks the white/black texture) and that mesh's Pos table.
*/
function promote(pawn, square, type){
    const typeName = PIECE_NAMES[type];
    const meshes = Scene.objects.filter(o => o.name && o.name.startsWith(typeName) && o.vertexBuff);
    // same colour first: knights are modelled facing their own side
    const template = meshes.filter(o => o.side === pawn.side)[0] || meshes[0];
    const piece = {
        name: template.name,
        side: pawn.side,
        location: Pos[template.name][square].slice(),
        cord: square,
        Selected: false,
        isVisible: false,
        promoted: true,
        normals: template.normals,
        vertexBuff: template.vertexBuff,
        IdxBuffer: template.IdxBuffer,
        textBuff: template.textBuff
    };
    Scene.objects.push(piece);
    pawn.cord = "";

    // swap models once the pawn reaches the last rank, unless the new piece was taken in the
    // meantime or the board was reset (a replay jumping elsewhere) before it landed
    const generation = boardGeneration;
    whenLanded(pawn, () => {
        if(generation !== boardGeneration) return;
        pawn.isVisible = false;
        if(piece.cord) piece.isVisible = true;
    });
}

/*
    localMove is the {from, to} that was sent; move is the server's verbose
    chess.js result, which is what knows about captures, castling and promotion.
    instant applies the move without animating.
*/
function createAnime(localMove,move,instant){
    if(move){
        try{
            chess.move({from: move.from, to: move.to, promotion: move.promotion})

            //make the captured piece invisible; en passant takes the pawn beside the target square
            if(move.captured){
                let square = move.flags.includes("e") ? move.to[0] + move.from[1] : move.to;
                let captured = pieceAt(square);
                if(captured){
                    captured.isVisible = false;
                    captured.Selected = false;
                    captured.cord = "";
                }
            }

            //castling: k = king side (h -> f), q = queen side (a -> d)
            if(move.flags.includes("k") || move.flags.includes("q")){
                let rank = move.from[1];
                let kingSide = move.flags.includes("k");
                let rook = pieceAt((kingSide ? "h" : "a") + rank);
                if(rook) animateTo(rook, (kingSide ? "f" : "d") + rank, instant);
            }

            let piece = pieceAt(move.from);
            animateTo(piece, move.to, instant);
            if(move.promotion){
                promote(piece, move.to, move.promotion);
            }
            clearMoves();
        }
        catch(err){
            console.log(err);
        }
    }
}


function showMoves(from){

	let moves = chess.moves({'square' : from , verbose : true })
	console.log(moves);
	moves.forEach(function(move){
		let boxes = Scene.boxs.filter(x => x.name == move.to)
		boxes.forEach(b => b.isVisible = true);
		
	})

}
function showMove(from , to){
	
	let piece = Scene.objects.filter(x => x.cord == from)[0]
	let box = Scene.boxs.filter(x => x.name == to )[0]
	console.log(piece);
	piece.Selected = true;
	box.isVisible = true;

}
function clearMoves(){

	let boxs = Scene.boxs.filter(x => x.isVisible)
	let piece = Scene.objects.filter(x => x.Selected);
	piece.forEach(x => x.Selected = false);
	boxs.forEach(x => x.isVisible = false)

}


function initSkybox(attrList , uniformList){

	var fragmentShader = null; //= utils.getShader(gl, "shader-fs");
	var vertexShader = null; //= utils.getShader(gl, "shader-vs");
	
	Scene.loadna("/shader/skybox.vert" , function(text,url){
	    console.log(text);
		let shader = gl.createShader(gl.VERTEX_SHADER)
		gl.shaderSource(shader , text);
		gl.compileShader(shader);
		vertexShader = shader;
		
	});
	
	Scene.loadna("/shader/skybox.frag", function(text , url){
		
		let shader = gl.createShader(gl.FRAGMENT_SHADER);
		gl.shaderSource(shader , text);
		gl.compileShader(shader)
		fragmentShader = shader;
		
	
	});
	 
	skyPrg = gl.createProgram();
	gl.attachShader(skyPrg, vertexShader);
	gl.attachShader(skyPrg, fragmentShader);
	
	gl.linkProgram(skyPrg);
	
	if (!gl.getProgramParameter(skyPrg, gl.LINK_STATUS)) {
			

			alert("Could not initialise shaders");
	}
	
	
	for(var i=0, max = attrList.length; i <max; i+=1){
			skyPrg[attrList[i]] = gl.getAttribLocation(skyPrg, attrList[i]);
	}
	for(var i=0, max = uniformList.length; i < max; i +=1){
			skyPrg[uniformList[i]] = gl.getUniformLocation(skyPrg, uniformList[i]);
	}
	
	gl.uniform3fv(skyPrg.uLightPosition,   [0,5,20]);
	gl.uniform3fv(skyPrg.uLightAmbient,    [1.0,1.0,1.0,1.0]);
	gl.uniform4fv(skyPrg.uLightDiffuse,    [1.0,1.0,1.0,1.0]);
	gl.uniform1f(skyPrg.uAlpha, 1.0);
	//gl.uniform1i(skyPrg.uUseVertexColor, useVertexColors);
	gl.uniform1i(skyPrg.uUseLambert, true);
	
	//console.log(skyPrg)
}
	function renderSkyBox(){
		
		try{
			if(Scene.objects == undefined) return;
			var object = Scene.objects[0];
			//console.log(object)
			gl.useProgram(skyPrg);
			Scene.transforms.prg = skyPrg;
			
			Scene.transforms.updatePerspective();
			
			Scene.transforms.calculateModelView();
			Scene.transforms.push();
			mat4.scale(Scene.transforms.mvMatrix , [420.0,420.0,420.0]);
			mat4.rotate(Scene.transforms.mvMatrix  , 90 , [0,0,0])
			
			Scene.transforms.setMatrixUniforms();
			Scene.transforms.pop();
			
		
			//console.log(object.vertexBuff.itemSize)
			
			//gl.uniform4fv(skyPrg.uMaterialDiffuse, [1.0,1.0,1.0,1.0]);
			//gl.uniform4fv(skyPrg.uMaterialAmbient, [1.0,1.0,1.0,1.0]);
			//gl.uniform1i(skyPrg.uWireframe,false);
			
			
			//Setting attributes
			gl.enableVertexAttribArray(skyPrg.aVertexPosition);
		   // gl.disableVertexAttribArray(skyPrg.aVertexNormal);
			//gl.disableVertexAttribArray(skyPrg.aVertexColor);
		
			gl.bindBuffer(gl.ARRAY_BUFFER, object.vertexBuff);
			gl.vertexAttribPointer(skyPrg.aVertexPosition, object.vertexBuff.itemSize, gl.FLOAT, false, 0, 0);
			gl.enableVertexAttribArray(skyPrg.aVertexPosition);
			
			gl.enableVertexAttribArray(skyPrg.aVertexTextureCoords);
			gl.bindBuffer(gl.ARRAY_BUFFER, object.textBuff);
			gl.vertexAttribPointer(skyPrg.aVertexTextureCoords, object.textBuff.itemSize, gl.FLOAT, false, 0, 0);

			
			gl.activeTexture(gl.TEXTURE0);
			gl.bindTexture(gl.TEXTURE_CUBE_MAP, Texture.skybox);
			gl.uniform1i(skyPrg.uCubeSampler, 0);
			gl.texParameteri(gl.TEXTURE_CUBE_MAP, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
			gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);

			gl.bindBuffer(gl.ARRAY_BUFFER, object.normals);
			gl.vertexAttribPointer(skyPrg.aVertexNormal, object.normals.itemSize, gl.FLOAT, false, 0, 0);
			gl.enableVertexAttribArray(skyPrg.aVertexNormal);
			
			gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, object.IdxBuffer);
			gl.drawElements(gl.TRIANGLES, object.IdxBuffer.numItems, gl.UNSIGNED_SHORT,0)

			gl.bindBuffer(gl.ARRAY_BUFFER, null);
			gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, null);

	}catch(err)
	{
		console.log(err);
	}

	}
	
 function initLights(){
    
     /*

        These Thing where implemented setting the apporiapte values here
        and then having the graphics shader compute the phong model based
        off these values. The material values where provided by the json
        model so we just imported them

     */

    //if(materials != null){
        gl.uniform3f(shaderProgram.uLightPosition,    0.0, 0.0, 125.0); //changed
        gl.uniform4fv(shaderProgram.uLightAmbient,  [.5,.5,.5,1.0]);
        gl.uniform4fv(shaderProgram.uLightDiffuse,  [0.5,0.5,0.5,1.0]); 
        gl.uniform4fv(shaderProgram.uLightSpecular,  [0.5,0.5,0.5,1.0]);
		
        gl.uniform4fv(shaderProgram.uMaterialAmbient, [.5,.5,.5,1.0]); 
        gl.uniform4fv(shaderProgram.uMaterialDiffuse, [0.5,0.5,0.5,1.0]);
        gl.uniform4fv(shaderProgram.uMaterialSpecular,[0.5,0.5,0.5,1.0]);
		
        //gl.uniform1f(shaderProgram.uShininess, materials[5].value);
        loaded = false;
    
}

function main(){

	chess = new Chess();
    // /index?replay=<gameId> replays a saved game instead of starting a new one
    const replayId = new URLSearchParams(window.location.search).get("replay");
    if(replayId){
        replay = { game: null, sans: [], ply: 0, timer: null };
        enterReplayUi();
    }
    else{
        // an online game in progress comes first (leaving it would forfeit); a link may ask for a
        // computer game (?side=, e.g. "Play as Black" after a replay); otherwise the player chooses
        const requestedSide = new URLSearchParams(window.location.search).get("side");
        resumeOrStart(validSide(requestedSide || storedSide()), !!requestedSide);
    }
    loadUser().then(user => {
        myRating = user && user.rating != null ? user.rating : null;
        updateOnlineSub();   // the dialog may already be showing
    });
    resizeCanvas();
    utils.initGL(document.getElementById("canvas"))
    window.addEventListener("resize", resizeCanvas);
    // Keyboard controls live in CameraInteractor; keep the panel in sync with them
    window.addEventListener("keydown", () => { selectedFrom = null; refreshHud(); });

    utils.initShaders();
	initSkybox(["aVertexPosition",
					"aVertexNormal",
					"aVertexColor",
					"aVertexTextureCoords"] , 	
	
	[				"uPMatrix", 
					"uMVMatrix", 
					"uNMatrix",
					"uMaterialDiffuse",
					"uMaterialAmbient",
					"uLightAmbient",
					"uLightDiffuse",
					"uLightPosition",
					"uWireframe",
					"uAlpha",
					"uUseVertexColor",
					"uUseLambert",
					"uSampler",
					"uCubeSampler"
					])
	Scene.init();
	BoardClick.init(document.getElementById("canvas"), onSquareClicked, onSquareHovered);
	if(replay) startReplay(replayId);
	Scene.loadna('/chesspos.json' , function(chessPosition){
		Pos = JSON.parse(chessPosition);
		console.log(Pos)
	})
	Scene.loadna('/boxpos.json' , function(box){
		boxPos = JSON.parse(box);
	})
	

	Scene.load('/static/model/P2_model_ChessPiecesandBoardDone_mini.json' , Scene.loadModels);
	Scene.load('/static/model/texCube.json' , Scene.loadCube);
	Scene.load('/static/gandalf.json' , function(m){
		Scene.wizard = [];
		
		m.meshes.forEach(function(model , i )				{
				
					var vertices = new Float32Array(model.vertices);
					var normal = new Float32Array(model.normals);
					var faces = new Uint16Array([].concat.apply([] , model.faces));
					
					
					var textures = new Float32Array(model.texturecoords[0])
					
					
					let obj = { 
								'location' : [0,0,0],
								'Selected' : false ,
								'normals' : gl.createBuffer() , 
								'vertexBuff': gl.createBuffer() , 
								'IdxBuffer':gl.createBuffer(),
								'textBuff' : gl.createBuffer(),
								'isVisible' : true
							  };
					

					gl.bindBuffer(gl.ARRAY_BUFFER, obj.normals);
					gl.bufferData(gl.ARRAY_BUFFER, normal, gl.STATIC_DRAW);
					obj.normals.itemSize = 3;
					obj.normals.numItems = normal.length / 3;

					
					gl.bindBuffer(gl.ARRAY_BUFFER, obj.textBuff);
					gl.bufferData(gl.ARRAY_BUFFER, textures, gl.STATIC_DRAW);
					obj.textBuff.itemSize = 2;
					obj.textBuff.numItems = textures.length / 2;
					

					gl.bindBuffer(gl.ARRAY_BUFFER, obj.vertexBuff);
					gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);
					obj.vertexBuff.itemSize = 3;
					obj.vertexBuff.numItems = vertices.length / 3;

					
					gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, obj.IdxBuffer);
					gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, faces, gl.STATIC_DRAW);
					obj.IdxBuffer.itemSize = 1;
					obj.IdxBuffer.numItems = faces.length ;

					Scene.wizard.push(obj)
				});
		
	})
	
	
	
	Texture.init(["/static/textures/board.png" , "/static/textures/ASHSEN_2.GIF" , "/static/textures/ASHSEN_2BUMP.gif"])
	Texture.cubeMapTexture(["/static/Skybox/spire_rt.png" , "/static/Skybox/spire_lf.png" , "/static/Skybox/spire_ft.png" , "/static/Skybox/spire_bk.png" , "/static/Skybox/spire_dn.png" , "/static/Skybox/spire_up.png"]);
	Scene.transforms.prg = shaderProgram;
	console.log(Scene.transforms.prg);
	//animationInit();
	gl.clearColor(0.0,0.0,0.0,1.0);
    gl.enable(gl.DEPTH_TEST);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
	
	allMoves = playerMoves();
    refreshHud();
    tick();
	
}


window.onload = main


