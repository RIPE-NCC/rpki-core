function handleNav() {
    const sidebar = document.getElementById("sidebar");
    const expandButton = document.getElementById("expand-nav-button");
    const header = document.getElementById("sidebar-space");
    sidebar.classList.toggle("collapsed");
    expandButton.classList.toggle("rotate");
    header.classList.toggle("collapsed");
}
