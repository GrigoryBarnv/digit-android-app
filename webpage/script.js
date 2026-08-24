// Minimal enhancement script for the static page.
const menuToggle = document.querySelector(".menu-toggle");
const mobileMenu = document.querySelector(".mobile-menu");
const mobileBackdrop = document.querySelector(".mobile-menu-backdrop");

function closeMenu() {
  if (!menuToggle || !mobileMenu || !mobileBackdrop) return;
  mobileMenu.hidden = true;
  mobileBackdrop.hidden = true;
  menuToggle.setAttribute("aria-expanded", "false");
}

function openMenu() {
  if (!menuToggle || !mobileMenu || !mobileBackdrop) return;
  mobileMenu.hidden = false;
  mobileBackdrop.hidden = false;
  menuToggle.setAttribute("aria-expanded", "true");
}

if (menuToggle && mobileMenu && mobileBackdrop) {
  menuToggle.addEventListener("click", () => {
    if (mobileMenu.hidden) {
      openMenu();
    } else {
      closeMenu();
    }
  });

  mobileBackdrop.addEventListener("click", closeMenu);

  mobileMenu.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", closeMenu);
  });

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeMenu();
  });

  window.addEventListener("resize", () => {
    if (window.innerWidth > 820) closeMenu();
  });
}

document.querySelectorAll('a[href^="#"]').forEach((link) => {
  link.addEventListener("click", (event) => {
    const target = document.querySelector(link.getAttribute("href"));
    if (!target) return;
    event.preventDefault();
    target.scrollIntoView({ behavior: "smooth", block: "start" });
  });
});
