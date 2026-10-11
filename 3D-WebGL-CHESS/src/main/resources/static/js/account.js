/*
	The account page: choosing a username (required before playing) and editing the username,
	name and picture. Uses session.js for the badge, CSRF token and sign-in checks.
*/

const $id = id => document.getElementById(id);
const USERNAME = /^[A-Za-z0-9_]{3,20}$/;
const USERNAME_HINT = "3–20 letters, digits or underscores.";
const PROFILE_USERNAME_HINT = "What other players see, in your games old and new.";

var user = null;
var availabilityTimer = null;
var availabilityToken = 0;

function jsonRequest(method, url, body){
    return fetch(url, {
        method: method,
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken() },
        body: JSON.stringify(body)
    }).then(checkAuth);
}

function show(id){
    ["loading", "setup", "profile", "signedOut"].forEach(section => $id(section).hidden = section !== id);
}

function setHint(id, text, kind){
    const hint = $id(id);
    hint.textContent = text;
    hint.className = "field-hint" + (kind ? " " + kind : "");
}

function defaultHint(hintId){
    return hintId === "profileUsernameHint" ? PROFILE_USERNAME_HINT : USERNAME_HINT;
}

/* ---------- Username ---------- */

/** Checks a username as it is typed: the format here, whether it's free on the server. */
function usernameTyped(inputId, hintId){
    clearTimeout(availabilityTimer);
    const token = ++availabilityToken;
    const username = $id(inputId).value.trim();
    if(!username || (user && username === user.username)){ setHint(hintId, defaultHint(hintId)); return; }
    if(!USERNAME.test(username)){ setHint(hintId, USERNAME_HINT, "error"); return; }
    setHint(hintId, "Checking…");
    availabilityTimer = setTimeout(() => {
        fetch("/auth/username-available?username=" + encodeURIComponent(username))
        .then(re => re.ok ? re.json() : null)
        .then(result => {
            if(token !== availabilityToken || !result) return;
            if(result.available) setHint(hintId, username + " is available.", "ok");
            else setHint(hintId, username + " is taken.", "error");
        })
        .catch(() => { if(token === availabilityToken) setHint(hintId, defaultHint(hintId)); });
    }, 300);
}

/**
 * Saves the username typed into inputId; resolves to true when saved (or unchanged). Problems are
 * shown in hintId.
 */
async function saveUsername(inputId, hintId){
    const username = $id(inputId).value.trim();
    if(user && username === user.username) return true;
    if(!USERNAME.test(username)){
        setHint(hintId, USERNAME_HINT, "error");
        $id(inputId).focus();
        return false;
    }
    availabilityToken++;   // a check still on its way is out of date now
    const re = await jsonRequest("PUT", "/auth/user/username", { username: username });
    if(!re.ok){
        setHint(hintId, re.status === 409 ? username + " is taken." : re.status === 400 ? USERNAME_HINT
            : "Couldn't save your username. Try again.", "error");
        $id(inputId).focus();
        return false;
    }
    user = await re.json();
    setHint(hintId, defaultHint(hintId));
    return true;
}

/** The first-time form: choose a username, then go and play. */
async function chooseUsername(event){
    event.preventDefault();
    const button = $id("usernameSave");
    button.disabled = true;
    try{
        // a new player goes on to play; the profile is here whenever they come back
        if(await saveUsername("usernameInput", "usernameHint")) window.location.href = "/index";
    }
    catch(err){
        console.log(err);
        setHint("usernameHint", "Couldn't save your username. Try again.", "error");
    }
    finally{
        button.disabled = false;
    }
}

/* ---------- Profile ---------- */

function previewPicture(){
    const url = $id("pictureInput").value.trim();
    const preview = $id("picturePreview");
    const valid = /^https?:\/\/\S+$/i.test(url);
    preview.hidden = !valid;
    $id("pictureEmpty").hidden = valid;
    if(valid && preview.src !== url) preview.src = url;
}

function fillProfile(){
    $id("profileUsername").textContent = user.username;
    $id("profileEmail").textContent = user.email;
    $id("profileRating").textContent = user.rating;
    $id("profileRatedGames").textContent = user.ratedGames;
    $id("profileUsernameInput").value = user.username;
    $id("nameInput").value = user.name;
    $id("pictureInput").value = user.picture;
    previewPicture();
}

function setProfileStatus(text, kind){
    const status = $id("profileStatus");
    status.textContent = text;
    status.className = "form-status" + (kind ? " " + kind : "");
}

/** Saves the username (if changed), then the name and picture. */
async function saveProfile(event){
    event.preventDefault();
    const picture = $id("pictureInput").value.trim();
    if(picture && !/^https?:\/\/\S+$/i.test(picture)){
        setProfileStatus("The picture must be an http or https link.", "error");
        $id("pictureInput").focus();
        return;
    }
    const button = $id("profileSave");
    button.disabled = true;
    setProfileStatus("Saving…");
    try{
        if(!await saveUsername("profileUsernameInput", "profileUsernameHint")){
            setProfileStatus("Nothing was saved.", "error");
            return;
        }
        const re = await jsonRequest("PUT", "/auth/user/profile", { name: $id("nameInput").value, picture: picture });
        if(!re.ok){
            setProfileStatus(re.status === 400 ? "Check the name and picture link." : "Couldn't save. Try again.", "error");
            return;
        }
        user = await re.json();
        fillProfile();
        loadUser();   // the badge picks up the new username and picture
        setProfileStatus("Saved.", "ok");
    }
    catch(err){
        console.log(err);
        setProfileStatus("Couldn't save. Try again.", "error");
    }
    finally{
        button.disabled = false;
    }
}

/* ---------- Start ---------- */

async function refresh(){
    user = await loadUser();
    if(!user){
        show("signedOut");
        return;
    }
    $id("historyLink").hidden = $id("playLink").hidden = !user.username;
    if(!user.username){
        show("setup");
        $id("usernameInput").focus();
    }
    else{
        fillProfile();
        show("profile");
    }
}

refresh();
