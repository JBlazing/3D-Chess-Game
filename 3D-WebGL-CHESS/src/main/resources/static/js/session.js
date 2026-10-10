/*
	Session helpers shared by the signed-in pages: the CSRF token for POSTs, sending the user
	back to the login page when their session has ended, the account badge and signing out.
	The badge expects #session, #sessionName and #sessionPicture elements.
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

/** Fills in the account badge; resolves to the signed-in user, or null when signed out or sign-in is off. */
function loadUser(){
    return fetch("/auth/user")
    .then(re => re.status === 200 ? re.json() : null)
    .then(user => {
        if(!user) return null;
        document.getElementById("sessionName").textContent = user.name || user.email;
        const picture = document.getElementById("sessionPicture");
        if(user.picture) picture.src = user.picture; else picture.hidden = true;
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
