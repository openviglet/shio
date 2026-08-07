// The whole page, built after the document loaded. Nothing above is content.
const routes = {
  "/": { title: "Console", lead: "Everything on this page was created by this file." },
  "/pricing": { title: "Pricing", lead: "A second route the server never renders." },
};
function view(path) {
  const route = routes[path] ?? routes["/"];
  return `<main>
    <section class="hero"><h1>${route.title}</h1><p>${route.lead}</p><a href="/pricing">Pricing</a></section>
    <section class="features"><h2>Features</h2>
      <div class="card"><h3>Client-side</h3><p>Rendered by script.</p></div>
      <div class="card"><h3>Routed</h3><p>By the history API.</p></div>
    </section>
    <canvas id="chart" width="320" height="120"></canvas>
  </main>`;
}
document.getElementById("root").innerHTML = view(location.pathname);
const ctx = document.getElementById("chart").getContext("2d");
ctx.fillStyle = "#0b7285";
ctx.fillRect(0, 0, 320, 120);
