/**
 * Behavior of the markers in the source code view:
 * <ul>
 *   <li>A marker is selected by the fragment of the URL: either the ID of the marker or a line number (then all
 *   markers that contain the line are selected). If there is only one marker, then this marker is selected
 *   automatically. The view scrolls to the first selected marker.</li>
 *   <li>Each marker gets a button that copies the link to the marker (URL with the ID of the marker as fragment).
 *   </li>
 *   <li>The previous and next visible marker can be selected with the buttons of the tag 'prism:navigation' or with
 *   the keys 'k' and 'j'.</li>
 *   <li>Pages can hide markers (e.g., by a filter) by setting the attribute 'hidden' of the marker element. Afterwards,
 *   the page must dispatch the event 'prism-markers-changed' on the document. Then the highlight of all code blocks
 *   that contain only hidden markers is removed, and the navigation skips the hidden markers.</li>
 * </ul>
 */
(function () {
  const SELECTED = "prism-marker--selected";
  const HIDDEN_BLOCK = "prism-marked--hidden";
  const CHANGED_EVENT = "prism-markers-changed";
  const LINK_ICON =
    '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" ' +
    'stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M10 14a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1 1"/>' +
    '<path d="M14 10a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1-1"/></svg>';

  function getMarkers() {
    return Array.from(document.querySelectorAll(".prism-source .prism-marker"));
  }

  function getVisibleMarkers() {
    return getMarkers().filter((marker) => !marker.hidden);
  }

  function findMarkersOfFragment(markers) {
    const fragment = decodeURIComponent(window.location.hash.substring(1));
    if (fragment.length === 0) {
      return [];
    }
    const byId = markers.filter((marker) => marker.id === fragment);
    if (byId.length > 0) {
      return byId;
    }
    if (/^\d+$/.test(fragment)) {
      const line = Number(fragment);
      return markers.filter(
        (marker) =>
          line >= Number(marker.dataset.lineStart) &&
          line <= Number(marker.dataset.lineEnd),
      );
    }
    return [];
  }

  function getSelectedMarker() {
    return getMarkers().find((marker) => marker.classList.contains(SELECTED));
  }

  function selectMarkers() {
    const markers = getMarkers();
    let selected = findMarkersOfFragment(markers);
    if (selected.length === 0 && markers.length === 1) {
      selected = markers;
    }

    markers.forEach((marker) => {
      marker.classList.toggle(SELECTED, selected.includes(marker));
    });
    selected.forEach((marker) => {
      const details = marker.querySelector("details");
      if (details) {
        details.open = true;
      }
    });

    const target =
      selected.find((marker) => !marker.hidden) ||
      (markers.length === 0
        ? document.querySelector(".prism-source code.highlight")
        : null);
    if (target) {
      target.scrollIntoView({ block: "center" });
    }
    updateNavigation();
  }

  function selectMarker(marker) {
    history.replaceState(null, "", "#" + marker.id);
    selectMarkers();
  }

  function navigate(offset) {
    const visible = getVisibleMarkers();
    if (visible.length === 0) {
      return;
    }
    const index = visible.indexOf(getSelectedMarker());
    let next;
    if (index < 0) {
      next = offset > 0 ? 0 : visible.length - 1;
    } else {
      next = Math.min(Math.max(index + offset, 0), visible.length - 1);
    }
    selectMarker(visible[next]);
  }

  function updateNavigation() {
    const visible = getVisibleMarkers();
    const index = visible.indexOf(getSelectedMarker());
    document
      .querySelectorAll('[data-prism-navigation="position"]')
      .forEach((position) => {
        position.textContent =
          visible.length === 0
            ? "0 / 0"
            : (index < 0 ? "-" : index + 1) + " / " + visible.length;
      });
    document
      .querySelectorAll('[data-prism-navigation="previous"]')
      .forEach((button) => {
        button.disabled = visible.length === 0 || index === 0;
      });
    document
      .querySelectorAll('[data-prism-navigation="next"]')
      .forEach((button) => {
        button.disabled = visible.length === 0 || index === visible.length - 1;
      });
  }

  function updateHighlights() {
    document
      .querySelectorAll(".prism-source code[data-markers]")
      .forEach((block) => {
        const ids = block.dataset.markers.split(" ").filter((id) => id);
        const isVisible = ids.some((id) => {
          const marker = document.getElementById(id);
          return !marker || !marker.hidden;
        });
        block.classList.toggle(HIDDEN_BLOCK, !isVisible);
      });
  }

  function copyLink(marker, button, labels) {
    const url = new URL(window.location.href);
    url.hash = marker.id;
    navigator.clipboard.writeText(url.toString()).then(() => {
      selectMarker(marker);
      if (typeof notificationBar !== "undefined") {
        notificationBar.show(labels.done, notificationBar.SUCCESS);
      } else {
        button.setAttribute("aria-label", labels.done);
      }
    });
  }

  function addCopyButtons() {
    const source = document.querySelector(".prism-source");
    if (!source || !navigator.clipboard) {
      return;
    }
    const labels = {
      copy: source.dataset.copyLink,
      done: source.dataset.copyLinkDone,
    };
    getMarkers().forEach((marker) => {
      const header = marker.querySelector(".prism-marker__header");
      if (!header) {
        return;
      }
      const button = document.createElement("button");
      button.type = "button";
      button.className = "prism-marker__copy";
      button.setAttribute("aria-label", labels.copy);
      button.setAttribute("tooltip", labels.copy);
      button.innerHTML = LINK_ICON; // NOPMD - static SVG markup of the icon
      button.addEventListener("click", (event) => {
        // the button is part of the summary element: do not toggle the description
        event.preventDefault();
        event.stopPropagation();
        copyLink(marker, button, labels);
      });
      header.appendChild(button);
    });
  }

  function isTyping(event) {
    const target = event.target;
    return (
      target instanceof HTMLElement &&
      (target.isContentEditable ||
        ["INPUT", "SELECT", "TEXTAREA"].includes(target.tagName))
    );
  }

  function handleKey(event) {
    if (
      event.defaultPrevented ||
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      isTyping(event)
    ) {
      return;
    }
    if (event.key === "j") {
      navigate(1);
      event.preventDefault();
    } else if (event.key === "k") {
      navigate(-1);
      event.preventDefault();
    }
  }

  function addNavigation() {
    document
      .querySelectorAll('[data-prism-navigation="previous"]')
      .forEach((button) => {
        button.addEventListener("click", () => navigate(-1));
      });
    document
      .querySelectorAll('[data-prism-navigation="next"]')
      .forEach((button) => {
        button.addEventListener("click", () => navigate(1));
      });
    document.addEventListener("keydown", handleKey);
  }

  function initialize() {
    addCopyButtons();
    addNavigation();
    updateHighlights();
    selectMarkers();
  }

  if (document.readyState === "complete") {
    initialize();
  } else {
    window.addEventListener("load", initialize);
  }
  window.addEventListener("hashchange", selectMarkers);
  document.addEventListener(CHANGED_EVENT, () => {
    updateHighlights();
    updateNavigation();
  });
})();
