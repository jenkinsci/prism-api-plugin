/**
 * Behavior of the markers in the source code view:
 * <ul>
 *   <li>A marker is selected by the fragment of the URL: either the ID of the marker or a line number (then all
 *   markers that contain the line are selected). If there is only one marker, then this marker is selected
 *   automatically. The view scrolls to the first selected marker.</li>
 *   <li>Each marker gets a button that copies the link to the marker (URL with the ID of the marker as fragment).
 *   </li>
 * </ul>
 */
(function () {
  const SELECTED = "prism-marker--selected";
  const LINK_ICON =
    '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" ' +
    'stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M10 14a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1 1"/>' +
    '<path d="M14 10a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1-1"/></svg>';

  function getMarkers() {
    return Array.from(document.querySelectorAll(".prism-source .prism-marker"));
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
      selected[0] ||
      (markers.length === 0
        ? document.querySelector(".prism-source code.highlight")
        : null);
    if (target) {
      target.scrollIntoView({ block: "center" });
    }
  }

  function copyLink(marker, button, labels) {
    const url = new URL(window.location.href);
    url.hash = marker.id;
    navigator.clipboard.writeText(url.toString()).then(() => {
      history.replaceState(null, "", "#" + marker.id);
      selectMarkers();
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

  function initialize() {
    addCopyButtons();
    selectMarkers();
  }

  if (document.readyState === "complete") {
    initialize();
  } else {
    window.addEventListener("load", initialize);
  }
  window.addEventListener("hashchange", selectMarkers);
})();
