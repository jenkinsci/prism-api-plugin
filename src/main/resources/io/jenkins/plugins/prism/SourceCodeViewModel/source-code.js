/**
 * Client side layout and behavior of the source code view.
 *
 * <p>The server renders the source code as a single hidden code block (".prism-source__raw > code") and the boxes
 * of the markers as hidden elements (".prism-source__markers > .prism-marker"), see SourcePrinter. This script
 * creates the visible layout:</p>
 * <ul>
 *   <li>Prism colorizes the whole source code at once (so multi-line comments or strings are colorized correctly).
 *   Afterwards, the colorized code is split into lines: each line gets a line number (with a link "#L42") and the
 *   boxes of the markers are inserted after their anchor lines. If the server rendered an excerpt only, the attribute
 *   'data-first-line' of the raw code defines the number of the first line. The layout is created again whenever Prism has
 *   colorized the code (e.g., after a language definition has been loaded).</li>
 *   <li>The lines of the visible markers are highlighted with the color of the highest severity.</li>
 *   <li>A marker is selected by the fragment of the URL: either the ID of the marker or a line number ("42" or
 *   "L42", then all markers that contain the line are selected). If there is only one marker, then this marker is
 *   selected automatically. The view scrolls to the first selected marker (or line).</li>
 *   <li>Each marker gets a button that copies the link to the marker (URL with the ID of the marker as fragment).
 *   </li>
 *   <li>The previous and next visible marker can be selected with the floating buttons (tag 'prism:navigation') or
 *   with the keys 'k' and 'j'. The buttons are shown only if there are at least two visible markers. An additional
 *   floating button scrolls to the top of the page, it is shown if the page has been scrolled down.</li>
 *   <li>Pages can hide markers (e.g., by a filter) by setting the attribute 'hidden' of the marker element.
 *   Afterwards, the page must dispatch the event 'prism-markers-changed' on the document. Then the highlights of
 *   the lines are computed from the visible markers only, and the navigation skips the hidden markers.</li>
 *   <li>If the attribute 'data-collapse' of the view is 'true', then long sections of lines without visible markers
 *   are collapsed: only some lines of context around the marked lines (and around the line selected by the URL) are
 *   visible. A button replaces each collapsed section, it expands the section. If there are no visible markers, all
 *   lines are visible.</li>
 *   <li>Long files get an overview ruler next to the lines: it shows the positions of the visible markers in the
 *   file. Clicking a mark selects the marker.</li>
 * </ul>
 */
(function () {
  const SOURCE = ".prism-source";
  const RAW_CODE = ".prism-source__raw > code";
  const LINES = "prism-source__lines";
  const MARKER = ".prism-marker";
  const CARD = ".prism-marker:not(.prism-marker--empty)";
  const LINE = "prism-line";
  const SELECTED = "prism-marker--selected";
  const SELECTED_LINE = "prism-line--selected";
  const HIDDEN_MARK = "code-mark--hidden";
  const CHANGED_EVENT = "prism-markers-changed";
  const SEVERITIES = ["error", "warning", "info"];
  // the button to scroll to the top is shown if the page has been scrolled down by more than this number of pixels
  const SCROLL_THRESHOLD = 400;
  // the number of lines that are visible before and after a marked line if sections without markers are collapsed
  const CONTEXT_LINES = 3;
  // sections with fewer lines are not collapsed, since the button would need nearly the same space
  const MIN_COLLAPSED_LINES = 8;
  // the overview ruler is shown for files with at least this number of lines
  const RULER_MIN_LINES = 50;
  const GAP = "prism-gap";
  const RULER = "prism-ruler";
  const LINK_ICON =
    '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" ' +
    'stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M10 14a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1 1"/>' +
    '<path d="M14 10a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1-1"/></svg>';

  // the view scrolls only once to the selection of the URL, later layouts keep the scroll position
  const selection = { isInitial: true };
  // the sections that have been expanded by the user, they are not collapsed again
  const expansion = { ranges: [] };

  /**
   * Splits the (colorized) content of an element into lines. Elements that contain line breaks (e.g., a token of a
   * multi-line comment) are split as well: the element is closed at the end of a line and opened again (as a shallow
   * copy) at the start of the next line.
   *
   * @param {Element} root the element with the content to split
   * @returns {DocumentFragment[]} the content of each line
   */
  function splitLines(root) {
    const doc = root.ownerDocument;
    const lines = [];
    let current = doc.createDocumentFragment();
    let open = [];

    function getContainer() {
      return open.length > 0 ? open[open.length - 1] : current;
    }

    function startNewLine() {
      lines.push(current);
      current = doc.createDocumentFragment();
      const previous = open;
      open = [];
      previous.forEach((element) => {
        const copy = element.cloneNode(false);
        getContainer().appendChild(copy);
        open.push(copy);
      });
    }

    function walk(node) {
      Array.from(node.childNodes).forEach((child) => {
        if (child.nodeType === child.TEXT_NODE) {
          child.nodeValue.split("\n").forEach((part, index) => {
            if (index > 0) {
              startNewLine();
            }
            if (part.length > 0) {
              getContainer().appendChild(doc.createTextNode(part));
            }
          });
        } else if (child.nodeType === child.ELEMENT_NODE) {
          const copy = child.cloneNode(false);
          getContainer().appendChild(copy);
          open.push(copy);
          walk(child);
          open.pop();
        }
      });
    }

    walk(root);
    lines.push(current);
    return lines;
  }

  /**
   * Computes the highlight of each line from the visible markers: the highest severity and the IDs of the markers
   * that contain the line.
   *
   * @param {number} firstLine the number of the first line (the view might show an excerpt only)
   * @param {number} lineCount the number of lines
   * @param {{id: string, start: number, end: number, severity: string, visible: boolean}[]} markers the markers
   * @returns {{severity: (string|undefined), ids: string[]}[]} the state of each line, index 0 is the first line
   */
  function computeLineStates(firstLine, lineCount, markers) {
    const lastLine = firstLine + lineCount - 1;
    const states = Array.from({ length: lineCount }, () => ({
      severity: undefined,
      ids: [],
    }));
    markers
      .filter((marker) => marker.visible && marker.start >= 1)
      .forEach((marker) => {
        const start = Math.max(marker.start, firstLine);
        const end = Math.min(Math.max(marker.start, marker.end), lastLine);
        for (let line = start; line <= end; line++) {
          const state = states[line - firstLine];
          state.ids.push(marker.id);
          if (
            state.severity === undefined ||
            getRank(marker.severity) < getRank(state.severity)
          ) {
            state.severity = marker.severity;
          }
        }
      });
    return states;
  }

  /**
   * Computes the sections of lines that should be collapsed: all sections without visible markers, except some
   * lines of context around the marked lines and around the selected line. Sections that have been expanded by the
   * user and short sections are not collapsed. If there are no marked lines, then nothing is collapsed.
   *
   * @param {number} firstLine the number of the first line
   * @param {{severity: (string|undefined)}[]} states the state of each line, see computeLineStates
   * @param {number|undefined} selectedLine the line selected by the URL
   * @param {{start: number, end: number}[]} expandedRanges the sections expanded by the user
   * @returns {{start: number, end: number}[]} the sections to collapse (line numbers, inclusive)
   */
  function computeCollapsedRanges(
    firstLine,
    states,
    selectedLine,
    expandedRanges,
  ) {
    const count = states.length;
    if (!states.some((state) => state.severity !== undefined)) {
      return [];
    }
    const isVisible = new Array(count).fill(false);
    const showContext = (index) => {
      const from = Math.max(0, index - CONTEXT_LINES);
      const to = Math.min(count - 1, index + CONTEXT_LINES);
      for (let position = from; position <= to; position++) {
        isVisible[position] = true;
      }
    };
    states.forEach((state, index) => {
      if (state.severity !== undefined) {
        showContext(index);
      }
    });
    if (selectedLine !== undefined) {
      const index = selectedLine - firstLine;
      if (index >= 0 && index < count) {
        showContext(index);
      }
    }
    expandedRanges.forEach((range) => {
      const from = Math.max(0, range.start - firstLine);
      const to = Math.min(count - 1, range.end - firstLine);
      for (let position = from; position <= to; position++) {
        isVisible[position] = true;
      }
    });

    const ranges = [];
    let start = -1;
    for (let index = 0; index <= count; index++) {
      if (index < count && !isVisible[index]) {
        if (start < 0) {
          start = index;
        }
      } else if (start >= 0) {
        if (index - start >= MIN_COLLAPSED_LINES) {
          ranges.push({ start: firstLine + start, end: firstLine + index - 1 });
        }
        start = -1;
      }
    }
    return ranges;
  }

  function getRank(severity) {
    const rank = SEVERITIES.indexOf(severity);
    return rank < 0 ? SEVERITIES.indexOf("warning") : rank;
  }

  /**
   * Parses the fragment of the URL.
   *
   * @param {string} hash the fragment of the URL (including the leading '#')
   * @returns {{line: (number|undefined), id: (string|undefined)}} the selected line or the ID of the selected marker
   */
  function parseFragment(hash) {
    const fragment = decodeURIComponent((hash || "").replace(/^#/, ""));
    const line = /^L?(\d+)$/.exec(fragment);
    if (line) {
      return { line: Number(line[1]) };
    }
    return fragment.length > 0 ? { id: fragment } : {};
  }

  function toPosition(marker) {
    return {
      id: marker.id,
      start: Number(marker.dataset.lineStart),
      end: Number(marker.dataset.lineEnd),
      anchor: Number(marker.dataset.anchorLine),
      severity: marker.dataset.severity,
      visible: !marker.hidden,
    };
  }

  function getSource() {
    return document.querySelector(SOURCE);
  }

  function getLines() {
    const source = getSource();
    return source ? source.querySelector("." + LINES) : null;
  }

  function getMarkers() {
    const source = getSource();
    return source ? Array.from(source.querySelectorAll(MARKER)) : [];
  }

  function getCards() {
    const source = getSource();
    return source ? Array.from(source.querySelectorAll(CARD)) : [];
  }

  function getVisibleCards() {
    return getCards().filter((marker) => !marker.hidden);
  }

  function getLanguageClass(code) {
    const language = Array.from(code.classList).find((name) =>
      name.startsWith("language-"),
    );
    return language || "language-none";
  }

  function createLine(number, content) {
    const line = document.createElement("div");
    line.className = LINE;
    line.id = "L" + number;
    line.dataset.line = String(number);

    const link = document.createElement("a");
    link.className = "prism-line__number";
    link.href = "#L" + number;
    link.textContent = String(number);
    line.appendChild(link);

    const code = document.createElement("span");
    code.className = "prism-line__code";
    code.appendChild(content);
    line.appendChild(code);

    return line;
  }

  /**
   * Creates the visible layout of the source code view from the (colorized) raw code and the boxes of the markers.
   */
  function render() {
    const source = getSource();
    const raw = source ? source.querySelector(RAW_CODE) : null;
    if (!raw) {
      return;
    }
    const lineCount = Number(raw.dataset.lines) || 0;
    const firstLine = Number(raw.dataset.firstLine) || 1;
    const lastLine = firstLine + lineCount - 1;
    const contents = splitLines(raw).slice(0, lineCount);

    let lines = getLines();
    if (!lines) {
      lines = document.createElement("pre");
      raw.parentElement.after(lines);
    }
    lines.className = LINES + " " + getLanguageClass(raw);
    lines.style.setProperty(
      "--prism-line-number-digits",
      String(String(Math.max(lastLine, 1)).length),
    );

    const cardsByAnchor = new Map();
    getCards().forEach((card) => {
      const anchor = Math.min(
        Math.max(Number(card.dataset.anchorLine) || 0, firstLine - 1),
        lastLine,
      );
      if (!cardsByAnchor.has(anchor)) {
        cardsByAnchor.set(anchor, []);
      }
      cardsByAnchor.get(anchor).push(card);
    });

    const children = [...(cardsByAnchor.get(firstLine - 1) || [])];
    for (let number = firstLine; number <= lastLine; number++) {
      children.push(
        createLine(
          number,
          contents[number - firstLine] || document.createDocumentFragment(),
        ),
      );
      children.push(...(cardsByAnchor.get(number) || []));
    }
    lines.replaceChildren(...children);

    updateHighlights();
    selectMarkers();
  }

  /**
   * Highlights the lines of the visible markers and hides the column marks of hidden markers.
   */
  function updateHighlights() {
    const lines = getLines();
    if (!lines) {
      return;
    }
    const rows = Array.from(lines.querySelectorAll(":scope > ." + LINE));
    const firstLine = rows.length > 0 ? Number(rows[0].dataset.line) : 1;
    const states = computeLineStates(
      firstLine,
      rows.length,
      getMarkers().map(toPosition),
    );
    rows.forEach((row, index) => {
      const severity = states[index].severity;
      row.classList.toggle(LINE + "--marked", severity !== undefined);
      SEVERITIES.forEach((name) => {
        row.classList.toggle(LINE + "--" + name, severity === name);
      });
    });
    updateCollapsedLines(rows, states, firstLine);
    updateRuler(firstLine, rows.length);
    lines.querySelectorAll(".code-mark[data-markers]").forEach((mark) => {
      const isVisible = mark.dataset.markers
        .split(" ")
        .filter((id) => id)
        .some((id) => {
          const marker = document.getElementById(id);
          return !marker || !marker.hidden;
        });
      mark.classList.toggle(HIDDEN_MARK, !isVisible);
    });
  }

  function createGap(range, label) {
    const gap = document.createElement("div");
    gap.className = GAP;
    gap.dataset.gapStart = String(range.start);
    gap.dataset.gapEnd = String(range.end);

    const button = document.createElement("button");
    button.type = "button";
    button.className = GAP + "__button";
    button.textContent = (label || "{0}").replace(
      "{0}",
      String(range.end - range.start + 1),
    );
    button.addEventListener("click", () => {
      expansion.ranges.push(range);
      updateHighlights();
    });
    gap.appendChild(button);
    return gap;
  }

  /**
   * Collapses the long sections of lines without visible markers, if enabled for the view.
   *
   * @param {HTMLElement[]} rows the lines of the view
   * @param {{severity: (string|undefined)}[]} states the state of each line
   * @param {number} firstLine the number of the first line
   */
  function updateCollapsedLines(rows, states, firstLine) {
    const source = getSource();
    const lines = getLines();
    lines.querySelectorAll(":scope > ." + GAP).forEach((gap) => gap.remove());
    rows.forEach((row) => {
      row.hidden = false;
    });
    if (source.dataset.collapse !== "true") {
      return;
    }
    const ranges = computeCollapsedRanges(
      firstLine,
      states,
      parseFragment(window.location.hash).line,
      expansion.ranges,
    );
    ranges.forEach((range) => {
      for (let line = range.start; line <= range.end; line++) {
        rows[line - firstLine].hidden = true;
      }
      rows[range.start - firstLine].before(
        createGap(range, source.dataset.expandLines),
      );
    });
  }

  function getMarkerTitle(marker) {
    const title = marker.querySelector(".prism-marker__title");
    return title ? title.textContent.trim() : "";
  }

  function scrollToMarker(marker, start) {
    if (marker.classList.contains("prism-marker--empty")) {
      const row = document.getElementById("L" + start);
      if (row) {
        row.scrollIntoView({ block: "center" });
      }
    } else {
      selectMarker(marker);
    }
  }

  /**
   * Shows the positions of the visible markers in an overview ruler next to the lines. The ruler is shown for long
   * files only.
   *
   * @param {number} firstLine the number of the first line
   * @param {number} lineCount the number of lines
   */
  function updateRuler(firstLine, lineCount) {
    const source = getSource();
    const lastLine = firstLine + lineCount - 1;
    const marks = getMarkers()
      .filter((marker) => !marker.hidden)
      .filter((marker) => {
        const position = toPosition(marker);
        return (
          position.start >= 1 &&
          position.start <= lastLine &&
          Math.max(position.start, position.end) >= firstLine
        );
      });

    let ruler = source.querySelector(":scope > ." + RULER);
    if (lineCount < RULER_MIN_LINES || marks.length === 0) {
      if (ruler) {
        ruler.remove();
      }
      source.classList.remove("prism-source--ruler");
      return;
    }
    if (!ruler) {
      ruler = document.createElement("div");
      ruler.className = RULER;
      ruler.setAttribute("role", "navigation");
      ruler.setAttribute("aria-label", source.dataset.ruler || "");
      const track = document.createElement("div");
      track.className = RULER + "__track";
      ruler.appendChild(track);
      source.appendChild(ruler);
    }
    source.classList.add("prism-source--ruler");

    const track = ruler.querySelector("." + RULER + "__track");
    const elements = marks.map((marker) => {
      const position = toPosition(marker);
      const start = Math.max(position.start, firstLine);
      const end = Math.min(Math.max(position.start, position.end), lastLine);
      const mark = document.createElement("button");
      mark.type = "button";
      mark.className =
        RULER + "__mark " + RULER + "__mark--" + position.severity;
      mark.dataset.marker = marker.id;
      mark.tabIndex = -1;
      mark.style.top = ((start - firstLine) / lineCount) * 100 + "%";
      mark.style.height = ((end - start + 1) / lineCount) * 100 + "%";
      const title = getMarkerTitle(marker) || "L" + start;
      mark.setAttribute("tooltip", title);
      mark.setAttribute("aria-label", title);
      mark.addEventListener("click", () => scrollToMarker(marker, start));
      return mark;
    });
    track.replaceChildren(...elements);
    updateRulerSelection();
  }

  function updateRulerSelection() {
    const selected = getSelectedMarker();
    document.querySelectorAll("." + RULER + "__mark").forEach((mark) => {
      mark.classList.toggle(
        RULER + "__mark--selected",
        selected !== undefined && mark.dataset.marker === selected.id,
      );
    });
  }

  function getSelectedMarker() {
    return getCards().find((marker) => marker.classList.contains(SELECTED));
  }

  function selectMarkers() {
    const cards = getCards();
    const fragment = parseFragment(window.location.hash);
    let selected = [];
    if (fragment.id) {
      selected = cards.filter((marker) => marker.id === fragment.id);
    } else if (fragment.line) {
      selected = cards.filter((marker) => {
        const position = toPosition(marker);
        return fragment.line >= position.start && fragment.line <= position.end;
      });
    }
    if (selected.length === 0 && !fragment.line && cards.length === 1) {
      selected = cards;
    }

    cards.forEach((marker) => {
      marker.classList.toggle(SELECTED, selected.includes(marker));
    });
    selected.forEach((marker) => {
      const details = marker.querySelector("details");
      if (details) {
        details.open = true;
      }
    });

    const lines = getLines();
    let selectedLine = null;
    if (lines) {
      lines.querySelectorAll("." + SELECTED_LINE).forEach((row) => {
        row.classList.remove(SELECTED_LINE);
      });
      if (fragment.line) {
        selectedLine = document.getElementById("L" + fragment.line);
        if (selectedLine && selectedLine.hidden) {
          // the line is part of a collapsed section: show the context of the line
          updateHighlights();
        }
        if (selectedLine) {
          selectedLine.classList.add(SELECTED_LINE);
        }
      }
    }

    if (selection.isInitial) {
      const target =
        selected.find((marker) => !marker.hidden) ||
        selectedLine ||
        (cards.length === 0 && lines
          ? lines.querySelector("." + LINE + "--marked")
          : null);
      if (target) {
        target.scrollIntoView({ block: "center" });
        selection.isInitial = false;
      }
    }
    updateRulerSelection();
    updateNavigation();
  }

  function selectMarker(marker) {
    history.replaceState(null, "", "#" + marker.id);
    selectMarkers();
    marker.scrollIntoView({ block: "center" });
  }

  function navigate(offset) {
    const visible = getVisibleCards();
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
    const visible = getVisibleCards();
    const index = visible.indexOf(getSelectedMarker());
    const hasMarkers = visible.length > 1;
    const isScrolled = window.scrollY > SCROLL_THRESHOLD;
    document.querySelectorAll(".prism-navigation").forEach((navigation) => {
      const isVisible = hasMarkers || isScrolled;
      navigation.classList.toggle("prism-navigation--visible", isVisible);
      navigation.classList.toggle("prism-navigation--markers", hasMarkers);
      navigation.classList.toggle("prism-navigation--scrolled", isScrolled);
      navigation.setAttribute("aria-hidden", String(!isVisible));
    });
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
    const source = getSource();
    if (!source || !navigator.clipboard) {
      return;
    }
    const labels = {
      copy: source.dataset.copyLink,
      done: source.dataset.copyLinkDone,
    };
    getCards().forEach((marker) => {
      const header = marker.querySelector(".prism-marker__header");
      if (!header || header.querySelector(".prism-marker__copy")) {
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
    document
      .querySelectorAll('[data-prism-navigation="top"]')
      .forEach((button) => {
        button.addEventListener("click", () =>
          window.scrollTo({ top: 0, behavior: "smooth" }),
        );
      });
    document.addEventListener("keydown", handleKey);
    // update the navigation at most once per frame while scrolling
    const scrolling = { isUpdateRequested: false };
    window.addEventListener(
      "scroll",
      () => {
        if (!scrolling.isUpdateRequested) {
          scrolling.isUpdateRequested = true;
          window.requestAnimationFrame(() => {
            scrolling.isUpdateRequested = false;
            updateNavigation();
          });
        }
      },
      { passive: true },
    );
  }

  function initialize() {
    const source = getSource();
    if (!source || source.dataset.prismInitialized === "true") {
      return;
    }
    source.dataset.prismInitialized = "true";
    addCopyButtons();
    addNavigation();
    render();
  }

  // Create the layout again, whenever Prism has colorized the raw code
  if (typeof Prism !== "undefined" && Prism.hooks) {
    Prism.hooks.add("complete", (env) => {
      const source = getSource();
      if (
        source &&
        source.dataset.prismInitialized === "true" &&
        env.element === source.querySelector(RAW_CODE)
      ) {
        render();
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initialize);
  } else {
    initialize();
  }
  window.addEventListener("hashchange", selectMarkers);
  document.addEventListener(CHANGED_EVENT, () => {
    updateHighlights();
    updateNavigation();
  });
})();
