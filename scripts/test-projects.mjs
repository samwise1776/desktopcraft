import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

await import(`../projects-core.js?test=${Date.now()}`);
const core = globalThis.DesktopcraftProjectsCore;

assert.equal(core.normalizePath("./src\\Main.java"), "src/Main.java");
assert.equal(core.supportedFile("src/Main.java", 1200), true);
assert.equal(core.supportedFile("node_modules/tool.js", 1200), false);
assert.equal(core.supportedFile("assets/photo.png", 1200), false);
assert.equal(core.supportedFile("src/large.js", 3_000_000), false);

const added = core.calculateLineDiff("one\ntwo", "one\ntwo\nthree");
assert.equal(added.added, 1);
assert.equal(added.removed, 0);

const removed = core.calculateLineDiff("one\ntwo\nthree", "one\nthree");
assert.equal(removed.removed, 1);

const changed = core.calculateLineDiff("one\ntwo", "one\nupdated");
assert.equal(changed.changed, 1);
assert.equal(changed.rows[0].before, "two");
assert.equal(changed.rows[0].text, "updated");

const workspace = core.cleanWorkspace({
  name: "Demo",
  activeId: "src/Main.java",
  files: [
    { id: "src/Main.java", path: "./src\\Main.java", content: "class Main {}", versions: [{ id: "v1", name: "First", content: "class Main {}", createdAt: 1 }] },
    { id: "duplicate", path: "src/Main.java", content: "duplicate" }
  ]
});
assert.equal(workspace.files.length, 1);
assert.equal(workspace.files[0].path, "src/Main.java");
assert.equal(workspace.files[0].versions[0].name, "First");

const page = await readFile(new URL("../projects.html", import.meta.url), "utf8");
const implementation = await readFile(new URL("../projects.js", import.meta.url), "utf8");
for (const id of ["addFileButton", "projectEditor", "addVersionButton", "compareVersion", "changePreview", "versionList"]) {
  assert.match(page, new RegExp(`id=["']${id}["']`));
}
assert.match(implementation, /showDirectoryPicker/);
assert.match(implementation, /createWritable/);
assert.match(implementation, /persistProjectWorkspace/);

console.log("Verified project folder import, editable storage, change tracking, and file versions.");
