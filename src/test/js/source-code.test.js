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
function createSource(lines, markers, language = "language-java", firstLine = 1) {
  const markerElements = markers
    .map((marker) => {
      const classes = `prism-marker prism-marker--${marker.severity}${marker.empty ? " prism-marker--empty" : ""}`;
      const content = marker.empty
        ? ""
        : `<div class="prism-marker__header"><span class="prism-marker__title">${marker.id}</span></div>`;
      return `<div id="${marker.id}" class="${classes}" data-line-start="${marker.start}"
          data-line-end="${marker.end}" data-anchor-line="${marker.anchor}"
          data-severity="${marker.severity}">${content}</div>`;
    })
    .join("");
  document.body.innerHTML = `
    <div class="prism-source" data-copy-link="Copy" data-copy-link-done="Copied">
      <pre class="prism-source__raw" hidden><code class="${language}" data-lines="${lines.length}" data-first-line="${firstLine}">${lines.join("\n")}</code></pre>
      <div class="prism-source__markers" hidden>${markerElements}</div>
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
  return ["error", "warning", "info"].find((severity) => row.classList.contains("prism-line--" + severity));
}

function getSelected() {
  return Array.from(document.querySelectorAll(".prism-marker--selected")).map((marker) => marker.id);
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
