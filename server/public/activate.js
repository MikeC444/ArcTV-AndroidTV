(function () {
  "use strict";

  var token = new URLSearchParams(window.location.search).get("token");

  var tabsEl = document.getElementById("tabs");
  var formEl = document.getElementById("form");
  var messageEl = document.getElementById("message");
  var submitButton = document.getElementById("submit");
  var displayNameField = document.getElementById("displayName");
  var passwordField = document.getElementById("password");
  var emailField = document.getElementById("email");
  var tabLogin = document.getElementById("tab-login");
  var tabRegister = document.getElementById("tab-register");
  var mode = "login";
  var nameWrap = document.getElementById("name-wrap");
  var submitLabel = document.getElementById("submit-label");
  var heading = document.getElementById("heading");
  var switchText = document.getElementById("switch-text");
  var switchBtn = document.getElementById("switch-btn");
  var switchEl = document.getElementById("switch");
  var noteEl = document.getElementById("note");
  var toggleBtn = document.getElementById("toggle");
  var deviceState = document.getElementById("device-state");
  var deviceDot = document.getElementById("device-dot");

  function setMessage(text, kind) {
    messageEl.textContent = text || "";
    messageEl.className = kind || "";
  }

  function hideForm() {
    formEl.style.display = "none";
    tabsEl.style.display = "none";
    switchEl.style.display = "none";
    noteEl.style.display = "none";
  }

  function setMode(next) {
    mode = next;
    tabLogin.classList.toggle("active", mode === "login");
    tabRegister.classList.toggle("active", mode === "register");
    nameWrap.hidden = mode !== "register";
    displayNameField.required = mode === "register";
    heading.textContent = mode === "register" ? "Create your account" : "Sign in to your TV";
    switchText.textContent = mode === "register" ? "Already have an account?" : "New to ArcTV?";
    switchBtn.textContent = mode === "register" ? "Sign in" : "Create an account";
    passwordField.autocomplete = mode === "register" ? "new-password" : "current-password";
    submitLabel.textContent = mode === "register" ? "Create account & connect TV" : "Sign in & connect TV";
    setMessage("");
  }

  switchBtn.addEventListener("click", function () {
    setMode(mode === "login" ? "register" : "login");
  });
  toggleBtn.addEventListener("click", function () {
    var show = passwordField.type === "password";
    passwordField.type = show ? "text" : "password";
    toggleBtn.setAttribute("aria-label", show ? "Hide password" : "Show password");
  });

  tabLogin.addEventListener("click", function () {
    setMode("login");
  });
  tabRegister.addEventListener("click", function () {
    setMode("register");
  });

  if (!token) {
    hideForm();
    setMessage("This link is missing its activation code. Go back to your TV and scan the QR code again.", "error");
    return;
  }

  fetch("/auth/qr/resolve?token=" + encodeURIComponent(token))
    .then(function (response) {
      return response.json();
    })
    .then(function (data) {
      if (data.status === "expired" || data.status === "not_found") {
        hideForm();
        deviceState.textContent = "Code expired";
        setMessage("This code has expired. Go back to your TV and try again.", "error");
      } else if (data.status === "consumed" || data.status === "completed") {
        hideForm();
        deviceState.textContent = "Connected";
        deviceDot.className = "dot ok";
        setMessage("This code has already been used. Check your TV — it may already be signed in.", "success");
      }
    })
    .catch(function () {
      // A failed pre-check doesn't block the form — submitting will
      // surface a clear error anyway, and a transient network hiccup here
      // shouldn't stop a legitimate attempt.
    });

  formEl.addEventListener("submit", function (event) {
    event.preventDefault();
    submitButton.disabled = true;
    setMessage("");

    var body = {
      token: token,
      mode: mode,
      email: emailField.value,
      password: passwordField.value,
    };
    if (mode === "register") {
      body.displayName = displayNameField.value.trim();
    }

    fetch("/auth/qr/complete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })
      .then(function (response) {
        if (response.status === 204) {
          hideForm();
          deviceState.textContent = "Connected";
          deviceDot.className = "dot ok";
          setMessage("You're signed in! Check your TV.", "success");
          return;
        }
        return response.json().then(function (data) {
          setMessage((data && data.error) || "Something went wrong. Please try again.", "error");
          submitButton.disabled = false;
        });
      })
      .catch(function () {
        setMessage("Couldn't reach the server. Check your connection and try again.", "error");
        submitButton.disabled = false;
      });
  });
})();
