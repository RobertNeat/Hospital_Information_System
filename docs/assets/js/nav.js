(function () {
  var NAV = [
    {
      group: "Start",
      items: [{ title: "Strona główna", href: "index.html" }],
    },
    {
      group: "Logowanie i konta",
      items: [
        { title: "Rejestracja konta", href: "pages/register.html" },
        { title: "Administracja personelem", href: "pages/admin-staff.html" },
      ],
    },
    {
      group: "Pacjent",
      items: [
        { title: "Panel główny (dashboard)", href: "pages/dashboard.html" },
        {
          title: "Krok rejestracji: przyjęcie",
          href: "pages/registration-admission-step.html",
        },
        { title: "Historia choroby", href: "pages/patient-history.html" },
        { title: "Wyniki badań", href: "pages/patient-results.html" },
      ],
    },
    {
      group: "Zlecenia laboratoryjne",
      items: [
        {
          title: "Kreator zlecenia laboratoryjnego",
          href: "pages/lab-order-wizard.html",
        },
        {
          title: "System zewnętrzny: e-laboratory",
          href: "pages/e-laboratory-external.html",
        },
      ],
    },
    {
      group: "Recepty",
      items: [
        {
          title: "Leki i recepty pacjenta",
          href: "pages/patient-prescriptions.html",
        },
        { title: "Kreator recepty", href: "pages/prescription-wizard.html" },
        {
          title: "System zewnętrzny: e-receipt",
          href: "pages/e-receipt-external.html",
        },
      ],
    },
    {
      group: "Badania obrazowe",
      items: [
        {
          title: "Kreator zlecenia obrazowego",
          href: "pages/imaging-order-wizard.html",
        },
        {
          title: "Opis badania obrazowego",
          href: "pages/imaging-report-view.html",
        },
        {
          title: "System zewnętrzny: e-imaging",
          href: "pages/e-imaging-external.html",
        },
      ],
    },
    {
      group: "Komunikacja",
      items: [
        {
          title: "Konwersacja (wiadomości)",
          href: "pages/message-conversation.html",
        },
      ],
    },
  ];

  function buildSidebar() {
    var root = document.body.getAttribute("data-root") || ".";
    var currentPage = document.body.getAttribute("data-page") || "";
    var sidebar = document.getElementById("sidebar");
    if (!sidebar) return;

    var html =
      '<a class="sidebar__brand" href="' +
      root +
      '/index.html">HIS – dokumentacja</a>';

    NAV.forEach(function (group) {
      html += '<div class="sidebar__group-title">' + group.group + "</div>";
      html += '<ul class="sidebar__list">';
      group.items.forEach(function (item) {
        var href = root + "/" + item.href;
        var isActive = currentPage && item.href.indexOf(currentPage) !== -1;
        html +=
          '<li><a href="' +
          href +
          '" class="' +
          (isActive ? "is-active" : "") +
          '">' +
          item.title +
          "</a></li>";
      });
      html += "</ul>";
    });

    sidebar.innerHTML = html;
  }

  document.addEventListener("DOMContentLoaded", buildSidebar);
})();
