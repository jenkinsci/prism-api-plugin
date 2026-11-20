/**
 * Selects the markers of the source code view. A marker is selected by the fragment of the URL: either the ID of
 * the marker or a line number (then all markers that contain the line are selected). If there is only one marker,
 * then this marker is selected automatically. The view scrolls to the first selected marker.
 */
(function () {
  const SELECTED = "prism-marker--selected";

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

  if (document.readyState === "complete") {
    selectMarkers();
  } else {
    window.addEventListener("load", selectMarkers);
  }
  window.addEventListener("hashchange", selectMarkers);
})();
