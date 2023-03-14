window.addEventListener('load', function() { 
    if (window.location.pathname.includes("history")) 
        document.getElementById("history").classList.add("active");
    else if (window.location.pathname.includes("upstream-ca")) 
        document.getElementById("management").classList.add("active");
    else if (window.location.pathname.includes("health-check")) 
        document.getElementById("health-check").classList.add("active");
    else if (window.location.pathname.includes("admin")) 
        document.getElementById("admin").classList.add("active");
});

function handleNav() {
    const sidebar = document.getElementById("sidebar");
    const expandButton = document.getElementById("expand-nav-button");
    const header = document.getElementById("sidebar-space");
    sidebar.classList.toggle("collapsed");
    expandButton.classList.toggle("rotate");
    header.classList.toggle("collapsed");
}