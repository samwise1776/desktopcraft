const projectsCore = window.DesktopcraftProjectsCore;
const projectDatabaseName = "desktopcraft-projects-v1";
const projectStoreName = "workspaces";
const projectFallbackKey = "desktopcraft-project-workspace-v1";
const projectHandles = new Map();

const projectElements = {
  addFile: document.querySelector("#addFileButton"),
  newFile: document.querySelector("#newFileButton"),
  folderInput: document.querySelector("#folderInput"),
  storageStatus: document.querySelector("#storageStatus"),
  name: document.querySelector("#projectName"),
  count: document.querySelector("#projectFileCount"),
  search: document.querySelector("#projectFileSearch"),
  files: document.querySelector("#projectFileList"),
  path: document.querySelector("#activeFilePath"),
  meta: document.querySelector("#activeFileMeta"),
  changeDot: document.querySelector("#projectChangeDot"),
  editor: document.querySelector("#projectEditor"),
  position: document.querySelector("#editorPosition"),
  saveStatus: document.querySelector("#editorSaveStatus"),
  save: document.querySelector("#saveFileButton"),
  download: document.querySelector("#downloadFileButton"),
  addVersion: document.querySelector("#addVersionButton"),
  compare: document.querySelector("#compareVersion"),
  added: document.querySelector("#addedCount"),
  removed: document.querySelector("#removedCount"),
  changed: document.querySelector("#changedCount"),
  changes: document.querySelector("#changePreview"),
  versionCount: document.querySelector("#versionCount"),
  versions: document.querySelector("#versionList"),
  newFileDialog: document.querySelector("#newFileDialog"),
  newFileForm: document.querySelector("#newFileForm"),
  newFilePath: document.querySelector("#newFilePath"),
  newFileError: document.querySelector("#newFileError"),
  versionDialog: document.querySelector("#versionDialog"),
  versionForm: document.querySelector("#versionForm"),
  versionName: document.querySelector("#versionName"),
  toast: document.querySelector("#projectToast")
};

let projectWorkspace = projectsCore.cleanWorkspace({ name: "Browser project", files: [] });
let projectSaveTimer;
let projectToastTimer;

function activeProjectFile() {
  return projectWorkspace.files.find((file) => file.id === projectWorkspace.activeId) || null;
}

function latestVersion(file) {
  return file?.versions?.at(-1) || null;
}

function isModified(file) {
  const baseline = latestVersion(file);
  return Boolean(file && (!baseline || baseline.content !== file.content));
}

function escapeProjectHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function showProjectToast(message) {
  projectElements.toast.textContent = message;
  projectElements.toast.classList.add("show");
  window.clearTimeout(projectToastTimer);
  projectToastTimer = window.setTimeout(() => projectElements.toast.classList.remove("show"), 2400);
}

function openProjectDatabase() {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) { reject(new Error("IndexedDB unavailable")); return; }
    const request = window.indexedDB.open(projectDatabaseName, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(projectStoreName)) request.result.createObjectStore(projectStoreName, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error("Could not open project storage"));
  });
}

async function loadProjectWorkspace() {
  try {
    const database = await openProjectDatabase();
    const saved = await new Promise((resolve, reject) => {
      const request = database.transaction(projectStoreName, "readonly").objectStore(projectStoreName).get("current");
      request.onsuccess = () => resolve(request.result?.workspace || null);
      request.onerror = () => reject(request.error || new Error("Could not read project storage"));
    });
    database.close();
    if (saved) return projectsCore.cleanWorkspace(saved);
  } catch {
    try {
      const saved = JSON.parse(localStorage.getItem(projectFallbackKey) || "null");
      if (saved) return projectsCore.cleanWorkspace(saved);
    } catch { /* Begin with an empty workspace. */ }
  }
  return projectsCore.cleanWorkspace({ name: "Browser project", files: [] });
}

async function persistProjectWorkspace() {
  const clean = projectsCore.cleanWorkspace(projectWorkspace);
  try {
    const database = await openProjectDatabase();
    await new Promise((resolve, reject) => {
      const request = database.transaction(projectStoreName, "readwrite").objectStore(projectStoreName).put({ id: "current", workspace: clean, updatedAt: Date.now() });
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error || new Error("Could not save project"));
    });
    database.close();
    projectElements.storageStatus.textContent = "Project files and versions save automatically in this browser.";
    projectElements.saveStatus.textContent = "Workspace saved";
  } catch {
    try {
      localStorage.setItem(projectFallbackKey, JSON.stringify(clean));
      projectElements.storageStatus.textContent = "Project saved in browser Web Storage.";
      projectElements.saveStatus.textContent = "Workspace saved";
    } catch {
      projectElements.storageStatus.textContent = "Browser storage is full. Download important files before leaving.";
      projectElements.saveStatus.textContent = "Could not save workspace";
    }
  }
}

function scheduleProjectSave() {
  projectElements.saveStatus.textContent = "Saving workspace…";
  window.clearTimeout(projectSaveTimer);
  projectSaveTimer = window.setTimeout(() => void persistProjectWorkspace(), 260);
}

function filePathParts(path) {
  const parts = path.split("/");
  return { name: parts.pop() || path, folder: parts.join("/") || "Project root" };
}

function renderProjectFiles() {
  const query = projectElements.search.value.trim().toLowerCase();
  const files = [...projectWorkspace.files].sort((left, right) => left.path.localeCompare(right.path)).filter((file) => file.path.toLowerCase().includes(query));
  projectElements.name.textContent = projectWorkspace.files.length ? projectWorkspace.name : "No folder selected";
  projectElements.count.textContent = `${projectWorkspace.files.length} file${projectWorkspace.files.length === 1 ? "" : "s"}`;
  if (!files.length) {
    projectElements.files.innerHTML = `<p class="project-empty-files">${projectWorkspace.files.length ? "No files match that search." : "Choose Add File to import a project folder."}</p>`;
    return;
  }
  projectElements.files.innerHTML = files.map((file) => {
    const parts = filePathParts(file.path);
    return `<button class="project-file-button${file.id === projectWorkspace.activeId ? " active" : ""}${isModified(file) ? " modified" : ""}" type="button" role="option" aria-selected="${file.id === projectWorkspace.activeId}" data-file-id="${escapeProjectHtml(file.id)}"><i></i><span><strong>${escapeProjectHtml(parts.name)}</strong><small>${escapeProjectHtml(parts.folder)}</small></span></button>`;
  }).join("");
}

function renderActiveProjectFile() {
  const file = activeProjectFile();
  const available = Boolean(file);
  projectElements.editor.disabled = !available;
  projectElements.save.disabled = !available;
  projectElements.download.disabled = !available;
  projectElements.addVersion.disabled = !available;
  projectElements.compare.disabled = !available || !file.versions.length;
  if (!file) {
    projectElements.path.textContent = "Choose a folder to begin";
    projectElements.meta.textContent = "";
    projectElements.editor.value = "";
    projectElements.saveStatus.textContent = "No file open";
    projectElements.changeDot.classList.remove("modified");
    renderProjectChanges();
    renderProjectVersions();
    return;
  }
  projectElements.path.textContent = file.path;
  projectElements.meta.textContent = `${file.content.length.toLocaleString()} characters · ${projectHandles.has(file.path) ? "connected to selected folder" : "browser workspace copy"}`;
  if (projectElements.editor.value !== file.content) projectElements.editor.value = file.content;
  projectElements.changeDot.classList.toggle("modified", isModified(file));
  renderProjectVersions();
  renderProjectChanges();
  updateEditorPosition();
}

function renderProjectVersions() {
  const file = activeProjectFile();
  const versions = file ? [...file.versions].reverse() : [];
  projectElements.versionCount.textContent = String(versions.length);
  const previousSelection = projectElements.compare.value;
  projectElements.compare.innerHTML = versions.length
    ? versions.map((version, index) => `<option value="${escapeProjectHtml(version.id)}">${index === 0 ? "Latest · " : ""}${escapeProjectHtml(version.name)}</option>`).join("")
    : "<option>No versions</option>";
  if (versions.some((version) => version.id === previousSelection)) projectElements.compare.value = previousSelection;
  projectElements.compare.disabled = !versions.length;
  projectElements.versions.innerHTML = versions.length
    ? versions.map((version, index) => `<article class="version-item"><div><strong>${escapeProjectHtml(version.name)}</strong><small>${new Date(version.createdAt).toLocaleString()}${index === 0 ? " · latest" : ""}</small></div><button type="button" data-restore-version="${escapeProjectHtml(version.id)}">Restore</button></article>`).join("")
    : '<p class="version-empty">Add a version to create a restorable snapshot.</p>';
}

function selectedBaseline(file) {
  return file?.versions.find((version) => version.id === projectElements.compare.value) || latestVersion(file);
}

function renderProjectChanges() {
  const file = activeProjectFile();
  const baseline = selectedBaseline(file);
  const diff = projectsCore.calculateLineDiff(baseline?.content || "", file?.content || "");
  projectElements.added.textContent = String(diff.added);
  projectElements.removed.textContent = String(diff.removed);
  projectElements.changed.textContent = String(diff.changed);
  if (!file) projectElements.changes.innerHTML = "<p>No file changes to show.</p>";
  else if (!baseline) projectElements.changes.innerHTML = "<p>Add the first version to establish a comparison point.</p>";
  else if (!diff.rows.length) projectElements.changes.innerHTML = "<p>No changes from this version.</p>";
  else {
    projectElements.changes.innerHTML = diff.rows.slice(0, 160).map((row) => `<div class="change-line ${row.type}"><span>${row.line}</span><b>${row.type === "added" ? "+" : row.type === "removed" ? "−" : "~"}</b><code>${row.type === "changed" ? `<span class="change-before">${escapeProjectHtml(row.before)}</span>\n${escapeProjectHtml(row.text)}` : escapeProjectHtml(row.text)}</code></div>`).join("") + (diff.rows.length > 160 ? `<p>Showing the first 160 of ${diff.rows.length} changed lines.</p>` : "");
  }
  projectElements.changeDot.classList.toggle("modified", isModified(file));
}

function selectProjectFile(id) {
  const file = projectWorkspace.files.find((candidate) => candidate.id === id);
  if (!file) return;
  projectWorkspace.activeId = file.id;
  renderProjectFiles();
  renderActiveProjectFile();
  projectElements.editor.focus();
  scheduleProjectSave();
}

function createImportedFile(path, content) {
  const timestamp = Date.now();
  return {
    id: path,
    path,
    content,
    updatedAt: timestamp,
    versions: [{ id: `version-${timestamp}-${Math.random().toString(16).slice(2)}`, name: "Imported", content, createdAt: timestamp }]
  };
}

async function fileRecord(file, path, handle = null) {
  if (!projectsCore.supportedFile(path, file.size)) return null;
  const content = await file.text();
  if (content.includes("\u0000")) return null;
  const cleanPath = projectsCore.normalizePath(path);
  if (handle) projectHandles.set(cleanPath, handle);
  return createImportedFile(cleanPath, content);
}

async function collectDirectoryFiles(directoryHandle, prefix = "", records = []) {
  for await (const [name, handle] of directoryHandle.entries()) {
    if (records.length >= 300) break;
    const path = projectsCore.normalizePath(prefix ? `${prefix}/${name}` : name);
    if (handle.kind === "directory") {
      if (![".git", "node_modules", ".idea", ".vscode", "__pycache__"].includes(name)) await collectDirectoryFiles(handle, path, records);
    } else {
      const file = await handle.getFile();
      const record = await fileRecord(file, path, handle);
      if (record) records.push(record);
    }
  }
  return records;
}

async function mergeImportedFiles(name, records) {
  let added = 0;
  let connected = 0;
  for (const record of records) {
    const existing = projectWorkspace.files.find((file) => file.path === record.path);
    if (existing) { connected++; continue; }
    projectWorkspace.files.push(record);
    added++;
  }
  projectWorkspace.name = name || projectWorkspace.name;
  if (!projectWorkspace.activeId && projectWorkspace.files.length) projectWorkspace.activeId = projectWorkspace.files[0].id;
  renderProjectFiles();
  renderActiveProjectFile();
  await persistProjectWorkspace();
  showProjectToast(`${added} file${added === 1 ? "" : "s"} added${connected ? ` · ${connected} reconnected` : ""}`);
}

async function chooseProjectFolder() {
  projectElements.addFile.disabled = true;
  try {
    if (window.showDirectoryPicker) {
      const directory = await window.showDirectoryPicker({ mode: "readwrite", id: "desktopcraft-project-folder" });
      const records = await collectDirectoryFiles(directory);
      await mergeImportedFiles(directory.name, records);
    } else {
      projectElements.folderInput.click();
    }
  } catch (error) {
    if (error?.name !== "AbortError") showProjectToast("That folder could not be opened");
  } finally {
    projectElements.addFile.disabled = false;
  }
}

async function importFallbackFolder(files) {
  const records = [];
  let rootName = "Imported folder";
  for (const file of [...files].slice(0, 300)) {
    const fullPath = projectsCore.normalizePath(file.webkitRelativePath || file.name);
    const parts = fullPath.split("/");
    if (parts.length > 1) rootName = parts.shift();
    const record = await fileRecord(file, parts.join("/") || file.name);
    if (record) records.push(record);
  }
  await mergeImportedFiles(rootName, records);
  projectElements.folderInput.value = "";
}

function downloadActiveFile() {
  const file = activeProjectFile();
  if (!file) return;
  const link = document.createElement("a");
  link.href = URL.createObjectURL(new Blob([file.content], { type: "text/plain;charset=utf-8" }));
  link.download = filePathParts(file.path).name;
  link.click();
  URL.revokeObjectURL(link.href);
  showProjectToast(`${link.download} downloaded`);
}

async function saveActiveFileToComputer() {
  const file = activeProjectFile();
  if (!file) return;
  const handle = projectHandles.get(file.path);
  if (!handle?.createWritable) { downloadActiveFile(); showProjectToast("Folder access ended, so a copy was downloaded"); return; }
  try {
    const writable = await handle.createWritable();
    await writable.write(file.content);
    await writable.close();
    projectElements.saveStatus.textContent = "Saved to selected folder";
    showProjectToast(`${filePathParts(file.path).name} saved to disk`);
  } catch {
    showProjectToast("The browser could not write that file. A download is still available.");
  }
}

function updateEditorPosition() {
  const before = projectElements.editor.value.slice(0, projectElements.editor.selectionStart);
  const lines = before.split("\n");
  projectElements.position.textContent = `Line ${lines.length}, column ${(lines.at(-1)?.length || 0) + 1}`;
}

projectElements.addFile.addEventListener("click", () => void chooseProjectFolder());
projectElements.folderInput.addEventListener("change", () => void importFallbackFolder(projectElements.folderInput.files));
projectElements.search.addEventListener("input", renderProjectFiles);
projectElements.files.addEventListener("click", (event) => {
  const button = event.target.closest("[data-file-id]");
  if (button) selectProjectFile(button.dataset.fileId);
});
projectElements.editor.addEventListener("input", () => {
  const file = activeProjectFile();
  if (!file) return;
  file.content = projectElements.editor.value;
  file.updatedAt = Date.now();
  projectElements.meta.textContent = `${file.content.length.toLocaleString()} characters · ${projectHandles.has(file.path) ? "connected to selected folder" : "browser workspace copy"}`;
  renderProjectFiles();
  renderProjectChanges();
  updateEditorPosition();
  scheduleProjectSave();
});
projectElements.editor.addEventListener("click", updateEditorPosition);
projectElements.editor.addEventListener("keyup", updateEditorPosition);
projectElements.editor.addEventListener("keydown", (event) => {
  if (event.key === "Tab") {
    event.preventDefault();
    const start = projectElements.editor.selectionStart;
    projectElements.editor.setRangeText("  ", start, projectElements.editor.selectionEnd, "end");
    projectElements.editor.dispatchEvent(new Event("input"));
  }
});
projectElements.compare.addEventListener("change", renderProjectChanges);
projectElements.save.addEventListener("click", () => void saveActiveFileToComputer());
projectElements.download.addEventListener("click", downloadActiveFile);

projectElements.newFile.addEventListener("click", () => {
  projectElements.newFileForm.reset();
  projectElements.newFileError.hidden = true;
  projectElements.newFileDialog.showModal();
  projectElements.newFilePath.focus();
});
projectElements.newFileForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const path = projectsCore.normalizePath(projectElements.newFilePath.value);
  if (!path || path.includes("..") || !projectsCore.supportedFile(path, 0)) {
    projectElements.newFileError.textContent = "Use a safe path with a supported text-file extension, such as src/Main.java.";
    projectElements.newFileError.hidden = false;
    return;
  }
  if (projectWorkspace.files.some((file) => file.path === path)) {
    projectElements.newFileError.textContent = "That file already exists in this project.";
    projectElements.newFileError.hidden = false;
    return;
  }
  const file = createImportedFile(path, "");
  file.versions[0].name = "Created";
  projectWorkspace.files.push(file);
  projectWorkspace.activeId = file.id;
  if (!projectWorkspace.name || projectWorkspace.name === "Browser project") projectWorkspace.name = "New project";
  projectElements.newFileDialog.close();
  renderProjectFiles(); renderActiveProjectFile(); scheduleProjectSave();
  showProjectToast(`${filePathParts(path).name} created`);
});

projectElements.addVersion.addEventListener("click", () => {
  const file = activeProjectFile();
  if (!file) return;
  projectElements.versionForm.reset();
  projectElements.versionName.value = `Version ${file.versions.length + 1}`;
  projectElements.versionDialog.showModal();
  projectElements.versionName.select();
});
projectElements.versionForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const file = activeProjectFile();
  const name = projectElements.versionName.value.trim();
  if (!file || !name) return;
  file.versions.push({ id: `version-${Date.now()}-${Math.random().toString(16).slice(2)}`, name, content: file.content, createdAt: Date.now() });
  file.versions = file.versions.slice(-40);
  projectElements.versionDialog.close();
  renderProjectFiles(); renderActiveProjectFile(); scheduleProjectSave();
  showProjectToast(`${name} added`);
});
projectElements.versions.addEventListener("click", (event) => {
  const button = event.target.closest("[data-restore-version]");
  const file = activeProjectFile();
  const version = file?.versions.find((candidate) => candidate.id === button?.dataset.restoreVersion);
  if (!file || !version || !window.confirm(`Restore “${version.name}”? Your current text stays recoverable only if you added it as a version.`)) return;
  file.content = version.content;
  file.updatedAt = Date.now();
  projectElements.editor.value = file.content;
  renderProjectFiles(); renderActiveProjectFile(); scheduleProjectSave();
  showProjectToast(`${version.name} restored`);
});

document.querySelectorAll("[data-close-dialog]").forEach((button) => button.addEventListener("click", () => button.closest("dialog").close()));
document.addEventListener("keydown", (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") { event.preventDefault(); void saveActiveFileToComputer(); }
  if ((event.ctrlKey || event.metaKey) && event.altKey && event.key.toLowerCase() === "v" && !projectElements.addVersion.disabled) { event.preventDefault(); projectElements.addVersion.click(); }
});

void (async () => {
  try { await window.navigator?.storage?.persist?.(); } catch { /* Browser persistence remains best effort. */ }
  projectWorkspace = await loadProjectWorkspace();
  if (!projectWorkspace.files.some((file) => file.id === projectWorkspace.activeId)) projectWorkspace.activeId = projectWorkspace.files[0]?.id || "";
  renderProjectFiles();
  renderActiveProjectFile();
})();
