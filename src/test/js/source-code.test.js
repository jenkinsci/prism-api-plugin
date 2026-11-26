import sourceCodeScript from "../../main/resources/io/jenkins/plugins/prism/SourceCodeViewModel/source-code.js?raw";
import { afterEach, beforeAll, describe, expect, test, vi } from "vitest";

/*
 * Tests the client side layout and behavior of the source code view (SourceCodeViewModel/source-code.js).
 *
 * source-code.js is a classic script (an adjunct), so it is evaluated in the global scope after the snippet of the
 * server (see SourcePrinter) has been added to the document, in the same way as the tests of Jenkins core do.
 */

const JAVA_SOURCE = [
  "public class Hello {",
  "    /* A comment",
  "       with two lines */",
  '    String text = "<b>text</b>";',
  "}",
];

// The listeners of the script are registered on the document and the window: they are removed after each test,
// since each test evaluates the script again
const listeners = [];

beforeAll(() => {
  // jsdom does not implement scrolling
  Element.prototype.scrollIntoView = vi.fn();
  window.scrollTo = vi.fn();
  [document, window].forEach((target) => {
    const addEventListener = target.addEventListener.bind(target);
    target.addEventListener = (type, listener, options) => {
      listeners.push({ target, type, listener, options });
      addEventListener(type, listener, options);
    };
  });
});

afterEach(() => {
  listeners.splice(0).forEach(({ target, type, listener, options }) => {
    target.removeEventListener(type, listener, options);
  });
  document.body.innerHTML = "";
  window.history.replaceState(null, "", "/");
  delete globalThis.Prism;
  vi.clearAllMocks();
});

/**
 * Evaluates the script in the global scope (indirect eval), as the browser does with an adjunct.
 */
function loadScript() {
  (0, eval)(sourceCodeScript);
}

function escape(text) {
  return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

/**
 * Creates the snippet that is rendered by the server (see SourcePrinter), together with the navigation.
 */
function createSource(lines, markers, language = "language-java", firstLine = 1, attributes = "", annotations = []) {
  const annotationElements =
    annotations.length === 0
      ? ""
      : `<div class="prism-source__annotations" hidden>${annotations
          .map(
            (annotation) =>
              `<span class="prism-line__annotation" data-line="${annotation.line}"` +
              `${annotation.tooltip ? ` tooltip="${annotation.tooltip}"` : ""}>${annotation.text}</span>`,
          )
          .join("")}</div>`;
  const markerElements = markers
    .map((marker) => {
      const classes =
        `prism-marker prism-marker--${marker.severity}${marker.empty ? " prism-marker--empty" : ""}` +
        `${marker.navigable ? " prism-marker--navigable" : ""}`;
      const content = marker.empty
        ? ""
        : `<div class="prism-marker__header"><span class="prism-marker__title">${marker.id}</span></div>`;
      return `<div id="${marker.id}" class="${classes}" data-line-start="${marker.start}"
          data-line-end="${marker.end}" data-anchor-line="${marker.anchor}"
          data-severity="${marker.severity}">${content}</div>`;
    })
    .join("");
  document.body.innerHTML = `
    <div class="prism-source" data-copy-link="Copy" data-copy-link-done="Copied" ${attributes}>
      <pre class="prism-source__raw" hidden><code class="${language}" data-lines="${lines.length}" data-first-line="${firstLine}">${lines.join("\n")}</code></pre>
      <div class="prism-source__markers" hidden>${markerElements}</div>
      ${annotationElements}
    </div>
    <div class="prism-navigation">
      <button data-prism-navigation="top"></button>
      <button data-prism-navigation="previous"></button>
      <span data-prism-navigation="position"></span>
      <button data-prism-navigation="next"></button>
    </div>`;
}

function getRows() {
  return Array.from(document.querySelectorAll(".prism-source__lines > .prism-line"));
}

function getCode(row) {
  return row.querySelector(".prism-line__code").innerHTML;
}

function getLayout() {
  return Array.from(document.querySelector(".prism-source__lines").children).map((element) =>
    element.classList.contains("prism-line") ? element.dataset.line : element.id,
  );
}

function markedSeverity(row) {
  if (!row.classList.contains("prism-line--marked")) {
    return undefined;
  }
  return ["error", "warning", "info", "success"].find((severity) =>
    row.classList.contains("prism-line--" + severity),
  );
}

function getSelected() {
  return Array.from(document.querySelectorAll(".prism-marker--selected")).map((marker) => marker.id);
}

function getSelectedLines() {
  return Array.from(document.querySelectorAll(".prism-line--selected")).map((row) => row.id);
}

function position() {
  return document.querySelector('[data-prism-navigation="position"]').textContent;
}

function pressKey(key) {
  document.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true }));
}

function notifyChangedMarkers() {
  document.dispatchEvent(new CustomEvent("prism-markers-changed"));
}

async function loadPrism() {
  // Prism must not colorize the document automatically
  window.Prism = { manual: true };
  const prism = (await import("prismjs")).default;
  await import("prismjs/components/prism-clike");
  await import("prismjs/components/prism-java");
  // the module is evaluated only once: in later tests, the global variable must be set again
  window.Prism = prism;
  return prism;
}

describe("splitting into lines", () => {
  test("should create a line for each line of the source code", () => {
    createSource(["first", "", "third"], []);

    loadScript();

    expect(getRows().map((row) => row.querySelector(".prism-line__code").textContent)).toEqual([
      "first",
      "",
      "third",
    ]);
    expect(getRows().map((row) => row.querySelector(".prism-line__number").textContent)).toEqual([
      "1",
      "2",
      "3",
    ]);
    expect(getRows()[0].querySelector("a").getAttribute("href")).toBe("#L1");
    expect(getRows()[0].id).toBe("L1");
  });

  test("should close and open again elements that span several lines", () => {
    createSource(
      [
        'int a; <span class="token comment">/* first',
        "second <b>bold",
        "third</b> */</span> int b;",
      ],
      [],
    );

    loadScript();

    expect(getRows().map(getCode)).toEqual([
      'int a; <span class="token comment">/* first</span>',
      '<span class="token comment">second <b>bold</b></span>',
      '<span class="token comment"><b>third</b> */</span> int b;',
    ]);
  });

  test("should keep the escaped text", () => {
    createSource(JAVA_SOURCE.map(escape), []);

    loadScript();

    expect(getRows()[3].querySelector(".prism-line__code").textContent).toBe('    String text = "<b>text</b>";');
    expect(document.querySelector(".prism-source__lines").className).toBe("prism-source__lines language-java");
  });

  test("should render a file without lines", () => {
    createSource([], [{ id: "top", start: 0, end: 0, anchor: 0, severity: "warning" }]);

    loadScript();

    expect(getLayout()).toEqual(["top"]);
  });
});

describe("layout of the markers", () => {
  const markers = [
    { id: "top", start: 0, end: 0, anchor: 0, severity: "info" },
    { id: "comment", start: 2, end: 3, anchor: 3, severity: "warning" },
    { id: "string", start: 4, end: 4, anchor: 4, severity: "error" },
    { id: "highlight-only", start: 1, end: 1, anchor: 1, severity: "info", empty: true },
  ];

  test("should insert the markers after their anchor lines", () => {
    createSource(JAVA_SOURCE.map(escape), markers);

    loadScript();

    expect(getLayout()).toEqual(["top", "1", "2", "3", "comment", "4", "string", "5"]);
    expect(document.querySelector(".prism-source__markers #highlight-only")).not.toBeNull();
  });

  test("should highlight the lines of the markers with the highest severity", () => {
    createSource(JAVA_SOURCE.map(escape), [
      ...markers,
      { id: "overlap", start: 3, end: 4, anchor: 4, severity: "info" },
    ]);

    loadScript();

    expect(getRows().map(markedSeverity)).toEqual(["info", "warning", "warning", "error", undefined]);
  });

  test("should highlight the lines of successful markers with the lowest severity", () => {
    createSource(JAVA_SOURCE.map(escape), [
      { id: "covered", start: 1, end: 3, anchor: 3, severity: "success", empty: true },
      { id: "partially", start: 3, end: 3, anchor: 3, severity: "warning", empty: true },
      { id: "info", start: 2, end: 2, anchor: 2, severity: "info", empty: true },
    ]);

    loadScript();

    expect(getRows().map(markedSeverity)).toEqual(["success", "info", "warning", undefined, undefined]);
  });

  test("should ignore markers outside of the file", () => {
    createSource(["one", "two"], [
      { id: "no-line", start: 0, end: 0, anchor: 0, severity: "error" },
      { id: "too-long", start: 2, end: 10, anchor: 2, severity: "info" },
    ]);

    loadScript();

    expect(getRows().map(markedSeverity)).toEqual([undefined, "info"]);
  });

  test("should colorize the whole file at once with Prism", async () => {
    createSource(JAVA_SOURCE.map(escape), markers);
    const prism = await loadPrism();

    loadScript();
    prism.highlightElement(document.querySelector(".prism-source__raw > code"));

    const comments = getRows()
      .slice(1, 3)
      .map((row) => Array.from(row.querySelectorAll(".token.comment")).map((token) => token.textContent));
    expect(comments).toEqual([["/* A comment"], ["       with two lines */"]]);
    expect(getRows()[3].querySelector(".token.string").textContent).toBe('"<b>text</b>"');
    expect(getLayout()).toEqual(["top", "1", "2", "3", "comment", "4", "string", "5"]);
  });
});

describe("annotations of the lines", () => {
  const annotations = [
    { line: 2, text: "1" },
    { line: 4, text: "2/4", tooltip: "Covered branches" },
  ];

  function getAnnotations() {
    return getRows().map((row) => row.querySelector(".prism-line__annotation").textContent);
  }

  test("should show the annotations in a separate column", () => {
    createSource(JAVA_SOURCE.map(escape), [], "language-java", 1, "", annotations);

    loadScript();

    const lines = document.querySelector(".prism-source__lines");
    expect(lines.classList.contains("prism-source__lines--annotated")).toBe(true);
    expect(lines.style.getPropertyValue("--prism-annotation-width")).toBe("3");
    expect(getAnnotations()).toEqual(["", "1", "", "2/4", ""]);
    expect(Array.from(getRows()[3].children).map((cell) => cell.className)).toEqual([
      "prism-line__number",
      "prism-line__annotation",
      "prism-line__code",
    ]);
    expect(getRows()[3].querySelector(".prism-line__annotation").getAttribute("tooltip")).toBe("Covered branches");
  });

  test("should not show a column without annotations", () => {
    createSource(JAVA_SOURCE.map(escape), []);

    loadScript();

    expect(document.querySelector(".prism-source__lines").classList.contains("prism-source__lines--annotated")).toBe(
      false,
    );
    expect(document.querySelector(".prism-line__annotation")).toBeNull();
  });

  test("should keep the annotations if the layout is created again", async () => {
    createSource(JAVA_SOURCE.map(escape), [], "language-java", 1, "", annotations);
    const annotation = document.querySelector('.prism-source__annotations [data-line="4"]');
    const prism = await loadPrism();

    loadScript();
    prism.highlightElement(document.querySelector(".prism-source__raw > code"));

    expect(getRows()[3].querySelector(".prism-line__annotation")).toBe(annotation);
    expect(getAnnotations()).toEqual(["", "1", "", "2/4", ""]);
  });
});

function createLines(count) {
  return Array.from({ length: count }, (_, index) => "line " + (index + 1));
}

const COLLAPSE = 'data-collapse="true" data-expand-lines="Show {0} lines"';

function getVisibleLines() {
  return getRows()
    .filter((row) => !row.hidden)
    .map((row) => Number(row.dataset.line));
}

function getGaps() {
  return Array.from(document.querySelectorAll(".prism-gap")).map(
    (gap) => `${gap.dataset.gapStart}-${gap.dataset.gapEnd}: ${gap.textContent}`,
  );
}

function range(from, to) {
  return Array.from({ length: to - from + 1 }, (_, index) => from + index);
}

describe("collapsing the lines without markers", () => {
  test("should show all lines if collapsing is not enabled", () => {
    createSource(createLines(40), [{ id: "m", start: 20, end: 20, anchor: 20, severity: "error" }], "");

    loadScript();

    expect(getVisibleLines()).toEqual(range(1, 40));
    expect(getGaps()).toEqual([]);
  });

  test("should collapse the lines without markers except the context", () => {
    createSource(
      createLines(40),
      [{ id: "m", start: 20, end: 21, anchor: 21, severity: "error" }],
      "",
      1,
      COLLAPSE,
    );

    loadScript();

    expect(getVisibleLines()).toEqual(range(17, 24));
    expect(getGaps()).toEqual(["1-16: Show 16 lines", "25-40: Show 16 lines"]);
    expect(document.querySelector(".prism-gap").nextElementSibling.id).toBe("L1");
  });

  test("should not collapse short sections", () => {
    createSource(
      createLines(30),
      [
        { id: "first", start: 8, end: 8, anchor: 8, severity: "error" },
        { id: "second", start: 18, end: 18, anchor: 18, severity: "info" },
      ],
      "",
      1,
      COLLAPSE,
    );

    loadScript();

    // lines 1-4 (4 lines) and 12-14 (3 lines) are too short, lines 22-30 (9 lines) are collapsed
    expect(getVisibleLines()).toEqual(range(1, 21));
    expect(getGaps()).toEqual(["22-30: Show 9 lines"]);
  });

  test("should expand a section", () => {
    createSource(
      createLines(40),
      [{ id: "m", start: 20, end: 20, anchor: 20, severity: "error" }],
      "",
      1,
      COLLAPSE,
    );
    loadScript();

    document.querySelector(".prism-gap__button").click();

    expect(getVisibleLines()).toEqual(range(1, 23));
    expect(getGaps()).toEqual(["24-40: Show 17 lines"]);

    // expanded sections remain visible if the markers change
    notifyChangedMarkers();
    expect(getVisibleLines()).toEqual(range(1, 23));
  });

  test("should show all lines if there are no visible markers", () => {
    createSource(
      createLines(40),
      [{ id: "m", start: 20, end: 20, anchor: 20, severity: "error" }],
      "",
      1,
      COLLAPSE,
    );
    loadScript();

    document.getElementById("m").hidden = true;
    notifyChangedMarkers();

    expect(getVisibleLines()).toEqual(range(1, 40));
    expect(getGaps()).toEqual([]);
  });

  test("should show the context of the selected line", () => {
    window.history.replaceState(null, "", "#L35");
    createSource(
      createLines(40),
      [{ id: "m", start: 5, end: 5, anchor: 5, severity: "error" }],
      "",
      1,
      COLLAPSE,
    );

    loadScript();

    // lines 1 and 39-40 are too short to be collapsed
    expect(getVisibleLines()).toEqual([...range(1, 8), ...range(32, 40)]);
    expect(getGaps()).toEqual(["9-31: Show 23 lines"]);
    expect(document.getElementById("L35").classList.contains("prism-line--selected")).toBe(true);
  });

  test("should expand a collapsed line that is selected later", () => {
    createSource(
      createLines(40),
      [{ id: "m", start: 5, end: 5, anchor: 5, severity: "error" }],
      "",
      1,
      COLLAPSE,
    );
    loadScript();
    expect(document.getElementById("L30").hidden).toBe(true);

    window.history.replaceState(null, "", "#L30");
    window.dispatchEvent(new HashChangeEvent("hashchange"));

    expect(document.getElementById("L30").hidden).toBe(false);
    expect(getVisibleLines()).toEqual([...range(1, 8), ...range(27, 40)]);
    expect(getGaps()).toEqual(["9-26: Show 18 lines"]);
  });
});

describe("overview ruler", () => {
  function getMarks() {
    return Array.from(document.querySelectorAll(".prism-ruler__mark")).map((mark) => ({
      marker: mark.dataset.marker,
      severity: mark.classList[1],
      top: mark.style.top,
      height: mark.style.height,
      title: mark.getAttribute("tooltip"),
    }));
  }

  test("should not show a ruler for short files", () => {
    createSource(createLines(49), [{ id: "m", start: 20, end: 20, anchor: 20, severity: "error" }], "");

    loadScript();

    expect(document.querySelector(".prism-ruler")).toBeNull();
    expect(document.querySelector(".prism-source").classList.contains("prism-source--ruler")).toBe(false);
  });

  test("should show the positions of the visible markers", () => {
    createSource(
      createLines(100),
      [
        { id: "first", start: 11, end: 20, anchor: 20, severity: "error" },
        { id: "second", start: 51, end: 51, anchor: 51, severity: "info" },
        { id: "hidden", start: 70, end: 70, anchor: 70, severity: "warning" },
        { id: "outside", start: 500, end: 500, anchor: 100, severity: "warning" },
      ],
      "",
    );
    document.getElementById("hidden").hidden = true;

    loadScript();

    expect(document.querySelector(".prism-source").classList.contains("prism-source--ruler")).toBe(true);
    expect(getMarks()).toEqual([
      { marker: "first", severity: "prism-ruler__mark--error", top: "10%", height: "10%", title: "first" },
      { marker: "second", severity: "prism-ruler__mark--info", top: "50%", height: "1%", title: "second" },
    ]);
  });

  test("should select the marker of a mark", () => {
    createSource(
      createLines(100),
      [
        { id: "first", start: 11, end: 11, anchor: 11, severity: "error" },
        { id: "second", start: 51, end: 51, anchor: 51, severity: "info" },
      ],
      "",
    );
    loadScript();

    document.querySelector('.prism-ruler__mark[data-marker="second"]').click();

    expect(getSelected()).toEqual(["second"]);
    expect(window.location.hash).toBe("#second");
    expect(
      document.querySelector('.prism-ruler__mark[data-marker="second"]').classList.contains("prism-ruler__mark--selected"),
    ).toBe(true);
  });

  test("should select a navigable marker without a box by its mark", () => {
    createSource(
      createLines(100),
      [
        { id: "highlight", start: 11, end: 11, anchor: 11, severity: "success", empty: true },
        { id: "missed", start: 51, end: 52, anchor: 52, severity: "error", empty: true, navigable: true },
      ],
      "",
    );
    loadScript();

    document.querySelector('.prism-ruler__mark[data-marker="highlight"]').click();
    expect(window.location.hash).toBe("");

    document.querySelector('.prism-ruler__mark[data-marker="missed"]').click();
    expect(window.location.hash).toBe("#missed");
    expect(getSelectedLines()).toEqual(["L51", "L52"]);
    expect(
      document.querySelector('.prism-ruler__mark[data-marker="missed"]').classList.contains("prism-ruler__mark--selected"),
    ).toBe(true);
  });

  test("should remove the ruler if all markers are hidden", () => {
    createSource(createLines(100), [{ id: "m", start: 20, end: 20, anchor: 20, severity: "error" }], "");
    loadScript();

    document.getElementById("m").hidden = true;
    notifyChangedMarkers();

    expect(document.querySelector(".prism-ruler")).toBeNull();
  });
});

describe("excerpts", () => {
  test("should number the lines of an excerpt and position the markers", () => {
    createSource(
      ["line 120", "line 121", "line 122"],
      [
        { id: "before", start: 3, end: 3, anchor: 119, severity: "info" },
        { id: "inside", start: 121, end: 121, anchor: 121, severity: "error" },
        { id: "overlapping", start: 100, end: 120, anchor: 120, severity: "warning" },
        { id: "after", start: 500, end: 500, anchor: 122, severity: "info" },
      ],
      "",
      120,
    );

    loadScript();

    expect(getLayout()).toEqual(["before", "120", "overlapping", "121", "inside", "122", "after"]);
    expect(getRows().map((row) => row.id)).toEqual(["L120", "L121", "L122"]);
    expect(getRows().map(markedSeverity)).toEqual(["warning", "error", undefined]);
    expect(document.querySelector(".prism-source__lines").className).toBe("prism-source__lines language-none");
  });

  test("should select a line of the excerpt", () => {
    window.history.replaceState(null, "", "#L121");
    createSource(["line 120", "line 121"], [{ id: "inside", start: 121, end: 121, anchor: 121, severity: "error" }], "", 120);

    loadScript();

    expect(document.getElementById("L121").classList.contains("prism-line--selected")).toBe(true);
    expect(getSelected()).toEqual(["inside"]);
  });
});

describe("filtering the markers", () => {
  test("should compute the highlight from the visible markers only", () => {
    createSource(JAVA_SOURCE.map(escape), [
      { id: "low", start: 2, end: 2, anchor: 2, severity: "info" },
      { id: "high", start: 2, end: 2, anchor: 2, severity: "error" },
    ]);
    loadScript();
    expect(markedSeverity(getRows()[1])).toBe("error");

    document.getElementById("high").hidden = true;
    notifyChangedMarkers();
    expect(markedSeverity(getRows()[1])).toBe("info");

    document.getElementById("low").hidden = true;
    notifyChangedMarkers();
    expect(markedSeverity(getRows()[1])).toBeUndefined();
  });

  test("should hide the column marks of hidden markers", () => {
    const lines = JAVA_SOURCE.map(escape);
    lines[3] = '    String text = <span class="code-mark" data-markers="string">"&lt;b&gt;text&lt;/b&gt;"</span>;';
    createSource(lines, [{ id: "string", start: 4, end: 4, anchor: 4, severity: "error" }]);
    loadScript();

    const mark = document.querySelector(".prism-source__lines .code-mark");
    expect(mark.textContent).toBe('"<b>text</b>"');
    expect(mark.classList.contains("code-mark--hidden")).toBe(false);

    document.getElementById("string").hidden = true;
    notifyChangedMarkers();

    expect(mark.classList.contains("code-mark--hidden")).toBe(true);
  });
});

describe("selection and navigation", () => {
  const markers = [
    { id: "first", start: 1, end: 1, anchor: 1, severity: "warning" },
    { id: "second", start: 3, end: 4, anchor: 4, severity: "error" },
    { id: "third", start: 5, end: 5, anchor: 5, severity: "info" },
  ];

  test("should select the marker of the fragment", () => {
    window.history.replaceState(null, "", "#second");
    createSource(JAVA_SOURCE.map(escape), markers);

    loadScript();

    expect(getSelected()).toEqual(["second"]);
    expect(position()).toBe("2 / 3");
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
  });

  test.each(["#L4", "#4"])("should select the line of the fragment %s and its markers", (fragment) => {
    window.history.replaceState(null, "", fragment);
    createSource(JAVA_SOURCE.map(escape), markers);

    loadScript();

    expect(getSelected()).toEqual(["second"]);
    expect(document.getElementById("L4").classList.contains("prism-line--selected")).toBe(true);
  });

  test("should select a single marker automatically", () => {
    createSource(JAVA_SOURCE.map(escape), [markers[1]]);

    loadScript();

    expect(getSelected()).toEqual(["second"]);
  });

  test("should navigate with the keys j and k and skip hidden markers", () => {
    createSource(JAVA_SOURCE.map(escape), markers);
    loadScript();
    expect(getSelected()).toEqual([]);
    expect(position()).toBe("- / 3");

    pressKey("j");
    expect(getSelected()).toEqual(["first"]);
    expect(window.location.hash).toBe("#first");

    document.getElementById("second").hidden = true;
    notifyChangedMarkers();
    pressKey("j");
    expect(getSelected()).toEqual(["third"]);
    expect(position()).toBe("2 / 2");

    pressKey("k");
    expect(getSelected()).toEqual(["first"]);
  });

  test("should navigate with the buttons", () => {
    createSource(JAVA_SOURCE.map(escape), markers);
    loadScript();

    document.querySelector('[data-prism-navigation="next"]').click();
    document.querySelector('[data-prism-navigation="next"]').click();

    expect(getSelected()).toEqual(["second"]);
    expect(document.querySelector('[data-prism-navigation="previous"]').disabled).toBe(false);
  });

  test("should not navigate while typing", () => {
    createSource(JAVA_SOURCE.map(escape), markers);
    document.body.insertAdjacentHTML("beforeend", '<input id="input">');
    loadScript();

    document.getElementById("input").dispatchEvent(new KeyboardEvent("keydown", { key: "j", bubbles: true }));

    expect(getSelected()).toEqual([]);
  });

  test("should navigate to the markers without a box that are navigable", () => {
    createSource(JAVA_SOURCE.map(escape), [
      { id: "covered", start: 1, end: 2, anchor: 2, severity: "success", empty: true },
      { id: "missed", start: 3, end: 4, anchor: 4, severity: "error", empty: true, navigable: true },
      { id: "partially", start: 5, end: 5, anchor: 5, severity: "warning", empty: true, navigable: true },
    ]);
    loadScript();
    expect(position()).toBe("- / 2");

    pressKey("j");
    expect(window.location.hash).toBe("#missed");
    expect(getSelectedLines()).toEqual(["L3", "L4"]);
    expect(Element.prototype.scrollIntoView.mock.contexts.at(-1)).toBe(document.getElementById("L3"));
    expect(position()).toBe("1 / 2");

    pressKey("j");
    expect(window.location.hash).toBe("#partially");
    expect(getSelectedLines()).toEqual(["L5"]);
  });

  test("should select a navigable marker without a box by the fragment", () => {
    window.history.replaceState(null, "", "#missed");
    createSource(JAVA_SOURCE.map(escape), [
      { id: "missed", start: 2, end: 3, anchor: 3, severity: "error", empty: true, navigable: true },
      { id: "other", start: 5, end: 5, anchor: 5, severity: "error", empty: true, navigable: true },
    ]);

    loadScript();

    expect(getSelected()).toEqual(["missed"]);
    expect(getSelectedLines()).toEqual(["L2", "L3"]);
  });

  test("should not highlight the lines of a hidden navigable marker", () => {
    window.history.replaceState(null, "", "#missed");
    createSource(JAVA_SOURCE.map(escape), [
      { id: "missed", start: 2, end: 3, anchor: 3, severity: "error", empty: true, navigable: true },
    ]);
    loadScript();

    document.getElementById("missed").hidden = true;
    window.dispatchEvent(new HashChangeEvent("hashchange"));

    expect(getSelectedLines()).toEqual([]);
  });

  test("should show the navigation only for at least two visible markers", () => {
    createSource(JAVA_SOURCE.map(escape), markers);
    loadScript();
    const navigation = document.querySelector(".prism-navigation");
    expect(navigation.classList.contains("prism-navigation--markers")).toBe(true);

    document.getElementById("first").hidden = true;
    document.getElementById("second").hidden = true;
    notifyChangedMarkers();

    expect(navigation.classList.contains("prism-navigation--markers")).toBe(false);
    expect(navigation.classList.contains("prism-navigation--visible")).toBe(false);
  });
});
