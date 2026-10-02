// Stripe sends people here after paying, or with ?cancelled=1 when they back out of checkout.
(function () {
  var cancelled = new URLSearchParams(window.location.search).has("cancelled");
  document.getElementById("paid").hidden = cancelled;
  document.getElementById("cancelled").hidden = !cancelled;
  document.title = cancelled ? "Checkout cancelled · ArcTV Plus" : "Thank you · ArcTV Plus";
})();
