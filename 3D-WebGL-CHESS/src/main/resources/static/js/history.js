/*
	The game history page (history.html): lists your games, with totals, filters and replay links.
	Loaded at the end of <body>, after the page's elements, so its top-level code can use them.
*/
// filled glyphs for both colours, styled per side; FE0E keeps them as text instead of emoji
const GLYPH = { k: "♚", q: "♛", r: "♜", b: "♝", n: "♞", p: "♟" };
const TEXT_STYLE = "︎";
const RESULTS = {
    win:  { label: "Win" },
    loss: { label: "Loss" },
    draw: { label: "Draw" },
    open: { label: "Unfinished" }
};
const FILTERS = [["all", "All"], ["win", "Wins"], ["loss", "Losses"], ["draw", "Draws"], ["open", "Unfinished"]];

let games = [];
let filter = "all";

function $id(id){ return document.getElementById(id); }

function el(tag, className, text){
    const node = document.createElement(tag);
    if(className) node.className = className;
    if(text !== undefined) node.textContent = text;
    return node;
}

function resultOf(game){
    if(game.status === "IN_PROGRESS") return "open";
    if(game.winner) return game.winner === game.color ? "win" : "loss";
    return "draw";
}

function endingOf(game){
    return { CHECKMATE: "Checkmate", STALEMATE: "Stalemate", DRAW: "Draw", RESIGNED: "Resignation",
             ABANDONED: "Abandoned", IN_PROGRESS: "Not finished" }[game.status] || game.status;
}

function capitalize(word){
    return word.charAt(0) + word.slice(1).toLowerCase();
}

// "3 hours ago" style, with the exact date and time as a tooltip
const relative = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });
function when(iso){
    const date = new Date(iso);
    const seconds = (date.getTime() - Date.now()) / 1000;
    const steps = [[60, "second"], [60, "minute"], [24, "hour"], [7, "day"], [4.35, "week"], [12, "month"], [Infinity, "year"]];
    let value = seconds;
    for(const [size, unit] of steps){
        if(Math.abs(value) < size) return { text: relative.format(Math.round(value), unit), title: date.toLocaleString() };
        value /= size;
    }
}

/** The position from the player's side of the board: black games are shown with black at the bottom. */
function miniBoard(fen, color){
    const board = el("div", "mini-board");
    board.setAttribute("role", "img");
    board.setAttribute("aria-label", "Final position");
    const rows = fen.split(" ")[0].split("/").map(row => {
        const cells = [];
        for(const ch of row){
            if(/\d/.test(ch)) for(let i = 0; i < Number(ch); i++) cells.push(null);
            else cells.push(ch);
        }
        return cells;
    });
    const flip = color === "BLACK";
    for(let r = 0; r < 8; r++){
        for(let f = 0; f < 8; f++){
            const rank = flip ? 7 - r : r, file = flip ? 7 - f : f;
            const square = el("span", (rank + file) % 2 === 0 ? "l" : "d");
            const piece = rows[rank][file];
            if(piece){
                square.textContent = GLYPH[piece.toLowerCase()] + TEXT_STYLE;
                square.classList.add(piece === piece.toUpperCase() ? "pw" : "pb");
            }
            board.appendChild(square);
        }
    }
    return board;
}

function moveList(moves){
    const list = el("ol", "moves");
    const plies = moves ? moves.split(" ") : [];
    if(plies.length === 0){
        list.appendChild(el("li", "none", "No moves were played."));
        return list;
    }
    for(let i = 0; i < plies.length; i += 2){
        const li = el("li");
        li.appendChild(el("span", "num", (i / 2 + 1) + "."));
        li.appendChild(document.createTextNode(plies[i] + (plies[i + 1] ? " " + plies[i + 1] : "")));
        list.appendChild(li);
    }
    return list;
}

function gameCard(game){
    const result = resultOf(game);
    const card = el("article", "game");
    card.appendChild(miniBoard(game.fen, game.color));

    const body = el("div", "game-body");
    const head = el("div", "game-head");
    head.appendChild(el("span", "badge " + result, RESULTS[result].label));
    const vs = el("span", "vs");
    vs.appendChild(el("span", "swatch " + game.color.toLowerCase()));
    vs.appendChild(document.createTextNode(capitalize(game.color) + " vs " + game.opponentName
        + (game.opponentRating != null ? " (" + game.opponentRating + ")" : "")));
    head.appendChild(vs);
    if(game.rated) head.appendChild(el("span", "tag", "Rated"));
    const time = when(game.updatedAt);
    const stamp = el("time", "when", time.text);
    stamp.dateTime = game.updatedAt;
    stamp.title = time.title;
    head.appendChild(stamp);
    body.appendChild(head);

    const fullMoves = Math.ceil(game.moveCount / 2);
    const metaRow = el("div", "meta-row");
    const meta = el("div", "meta", endingOf(game) + " · " + fullMoves + (fullMoves === 1 ? " move" : " moves"));
    if(game.ratingChange != null){
        meta.appendChild(document.createTextNode(" · "));
        meta.appendChild(el("span", game.ratingChange >= 0 ? "delta up" : "delta down",
            (game.ratingChange >= 0 ? "+" : "") + game.ratingChange + " rating"));
    }
    metaRow.appendChild(meta);
    if(game.moveCount > 0){
        const replayLink = el("a", "btn btn-sm", "▶︎ Replay");
        replayLink.href = "/index?replay=" + encodeURIComponent(game.gameId);
        replayLink.setAttribute("aria-label", "Replay this game on the 3D board");
        metaRow.appendChild(replayLink);
    }
    body.appendChild(metaRow);

    const details = el("details");
    details.appendChild(el("summary", null, "Show moves"));
    details.appendChild(moveList(game.moves));
    details.addEventListener("toggle", () => { details.querySelector("summary").textContent = details.open ? "Hide moves" : "Show moves"; });
    body.appendChild(details);

    card.appendChild(body);
    return card;
}

function render(){
    const counts = { all: games.length, win: 0, loss: 0, draw: 0, open: 0 };
    games.forEach(g => counts[resultOf(g)]++);

    $id("statPlayed").textContent = counts.all;
    $id("statWins").textContent = counts.win;
    $id("statLosses").textContent = counts.loss;
    $id("statDraws").textContent = counts.draw;
    $id("stats").hidden = games.length === 0;

    const filters = $id("filters");
    filters.textContent = "";
    FILTERS.forEach(([key, label]) => {
        const chip = el("button", "chip", label);
        chip.type = "button";
        chip.setAttribute("aria-pressed", String(filter === key));
        chip.appendChild(el("span", "count", String(counts[key])));
        chip.addEventListener("click", () => { filter = key; render(); });
        filters.appendChild(chip);
    });
    filters.hidden = games.length === 0;

    const shown = games.filter(g => filter === "all" || resultOf(g) === filter);
    const list = $id("games");
    list.textContent = "";
    shown.forEach(g => list.appendChild(gameCard(g)));

    const empty = $id("empty");
    empty.hidden = shown.length > 0;
    if(games.length === 0){
        $id("emptyTitle").textContent = "No games yet";
        $id("emptyText").textContent = "Games you play are saved here automatically.";
        $id("emptyAction").hidden = false;
    }
    else{
        $id("emptyTitle").textContent = "Nothing here";
        $id("emptyText").textContent = "None of your games match this filter.";
        $id("emptyAction").hidden = true;
    }
    $id("limitNote").hidden = games.length < 50;
}

function loadGames(){
    $id("error").hidden = true;
    $id("loading").hidden = false;
    fetch("/game/mine")
    .then(checkAuth)
    .then(re => { if(!re.ok) throw new Error("HTTP " + re.status); return re.json(); })
    .then(data => {
        games = data;
        $id("loading").hidden = true;
        render();
    })
    .catch(err => {
        console.log(err);
        $id("loading").hidden = true;
        $id("error").hidden = false;
    });
}

loadUser().then(user => {
    if(user && user.rating != null){
        $id("ratingPill").textContent = "Rating " + user.rating;
        $id("ratingPill").hidden = false;
    }
});
loadGames();
