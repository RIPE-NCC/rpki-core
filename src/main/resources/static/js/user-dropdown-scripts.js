function onUserIconClick() {
  document.getElementById("user-dropdown").classList.toggle("hidden");
}

// Close the dropdown if the user clicks outside of it
window.onclick = function (event) {
  if (!event.target.matches('.drop-icon')) {
    const dropdown = document.getElementById("user-dropdown");
    if (!dropdown.classList.contains('hidden')) {
      dropdown.classList.add('hidden');
    }
  }
}