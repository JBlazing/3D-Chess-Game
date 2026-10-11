/*
	Session helpers shared by the signed-in pages: the CSRF token for POSTs, sending the user
	back to the login page when their session has ended, the account badge and signing out.
	The badge expects #session, #sessionName and #sessionPicture elements. A signed-in player
	without a username is sent to the account page to choose one.
*/

function csrfToken(){
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : "";
}

// Session expired or not logged in: go back to the login page
function checkAuth(re){
    if(re.status === 401){
        window.location.href = "/";
        throw new Error("Not logged in");
    }
    return re;
}

/**
 * Fills in the account badge; resolves to the signed-in user, or null when signed out or sign-in is off.
 * Other players see the username, so that's what the badge shows.
 */
function loadUser(){
    return fetch("/auth/user")
    .then(re => re.status === 200 ? re.json() : null)
    .then(user => {
        if(!user) return null;
        if(!user.username && window.location.pathname !== "/account"){
            window.location.href = "/account";
            return user;
        }
        document.getElementById("sessionName").textContent = user.username || user.name || user.email;
        const picture = document.getElementById("sessionPicture");
        picture.hidden = !user.picture;
        if(user.picture) picture.src = user.picture;
        const rating = document.getElementById("sessionRating");
        if(rating && user.rating != null){
            rating.textContent = user.rating;
            rating.hidden = false;
        }
        document.getElementById("session").hidden = false;
        return user;
    })
    .catch(() => null);
}

function signOut(){
    fetch("/logout", {
        method: "POST",
        headers: { 'X-XSRF-TOKEN': csrfToken() }
    }).finally(() => window.location.href = "/");
}
