// Spec 024 consent fixtures — every button carries data-choice (reject | accept | settings).
// A click records the choice in localStorage and the title, and closes the banner.
(function () {
  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-choice]');
    if (!button) return;
    var choice = button.getAttribute('data-choice');
    var previous = localStorage.getItem('fixtureChoice');
    var value = previous ? previous + '>' + choice : choice;
    localStorage.setItem('fixtureChoice', value);
    document.title = 'choice:' + value;
    if (choice !== 'settings') {
      var banner = button.closest('[data-banner]');
      if (banner) banner.style.display = 'none';
    }
  }, true);
  if (location.search.indexOf('reset') >= 0) localStorage.removeItem('fixtureChoice');
})();
