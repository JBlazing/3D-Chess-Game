/* The sign-in page (login.html). Loaded at the end of <body>, after the error message element. */
// Spring Security redirects here with ?error when the Google login fails
const params = new URLSearchParams(window.location.search);
if (params.has("error")) {
    const error = document.getElementById("error");
    error.textContent = "Sign-in didn't complete. Please try again.";
    error.hidden = false;
}
