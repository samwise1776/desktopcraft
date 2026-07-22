(function (root) {
  const textExtensions = new Set([
    "txt", "md", "markdown", "java", "py", "cs", "cpp", "cc", "c", "h", "hpp", "js", "mjs", "cjs", "jsx", "ts", "tsx",
    "html", "htm", "css", "scss", "sass", "less", "json", "jsonc", "xml", "svg", "yml", "yaml", "toml", "ini", "cfg", "conf",
    "properties", "gradle", "kts", "sql", "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "rb", "php", "go", "rs", "swift",
    "kt", "dart", "lua", "r", "vue", "svelte", "gitignore", "dockerignore", "editorconfig", "env", "csv", "tsv"
  ]);
  const textNames = new Set(["dockerfile", "makefile", "readme", "license", "notice", "procfile", "gemfile", "rakefile"]);

  function normalizePath(value) {
    return String(value || "").replaceAll("\\", "/").replace(/^\.\//, "").split("/").filter((part) => part && part !== ".").join("/");
  }

  function supportedFile(path, size = 0) {
    const clean = normalizePath(path);
    const name = clean.split("/").pop()?.toLowerCase() || "";
    const extension = name.includes(".") ? name.split(".").pop() : name;
    return Boolean(clean) && Number(size) <= 2_000_000 && !clean.split("/").some((part) => [".git", "node_modules", ".idea", ".vscode", "__pycache__"].includes(part))
      && (textExtensions.has(extension) || textNames.has(name));
  }

  function simpleLineDiff(before, after) {
    const oldLines = String(before).split("\n");
    const newLines = String(after).split("\n");
    const rows = [];
    let added = 0;
    let removed = 0;
    let changed = 0;
    const count = Math.max(oldLines.length, newLines.length);
    for (let index = 0; index < count; index++) {
      if (index >= oldLines.length) { rows.push({ type: "added", text: newLines[index], line: index + 1 }); added++; }
      else if (index >= newLines.length) { rows.push({ type: "removed", text: oldLines[index], line: index + 1 }); removed++; }
      else if (oldLines[index] !== newLines[index]) { rows.push({ type: "changed", before: oldLines[index], text: newLines[index], line: index + 1 }); changed++; }
    }
    return { rows, added, removed, changed };
  }

  function calculateLineDiff(before, after) {
    const oldLines = String(before).split("\n");
    const newLines = String(after).split("\n");
    if (oldLines.length > 320 || newLines.length > 320) return simpleLineDiff(before, after);
    const table = Array.from({ length: oldLines.length + 1 }, () => new Uint16Array(newLines.length + 1));
    for (let oldIndex = oldLines.length - 1; oldIndex >= 0; oldIndex--) {
      for (let newIndex = newLines.length - 1; newIndex >= 0; newIndex--) {
        table[oldIndex][newIndex] = oldLines[oldIndex] === newLines[newIndex]
          ? table[oldIndex + 1][newIndex + 1] + 1
          : Math.max(table[oldIndex + 1][newIndex], table[oldIndex][newIndex + 1]);
      }
    }
    const raw = [];
    let oldIndex = 0;
    let newIndex = 0;
    while (oldIndex < oldLines.length || newIndex < newLines.length) {
      if (oldIndex < oldLines.length && newIndex < newLines.length && oldLines[oldIndex] === newLines[newIndex]) {
        oldIndex++; newIndex++; continue;
      }
      if (newIndex < newLines.length && (oldIndex === oldLines.length || table[oldIndex][newIndex + 1] >= table[oldIndex + 1][newIndex])) {
        raw.push({ type: "added", text: newLines[newIndex], line: newIndex + 1 }); newIndex++;
      } else {
        raw.push({ type: "removed", text: oldLines[oldIndex], line: oldIndex + 1 }); oldIndex++;
      }
    }
    const rows = [];
    let changed = 0;
    for (let index = 0; index < raw.length; index++) {
      if (raw[index + 1] && raw[index].type !== raw[index + 1].type) {
        const removedRow = raw[index].type === "removed" ? raw[index] : raw[index + 1];
        const addedRow = raw[index].type === "added" ? raw[index] : raw[index + 1];
        rows.push({ type: "changed", before: removedRow.text, text: addedRow.text, line: addedRow.line });
        changed++; index++;
      } else rows.push(raw[index]);
    }
    return {
      rows,
      added: rows.filter((row) => row.type === "added").length,
      removed: rows.filter((row) => row.type === "removed").length,
      changed
    };
  }

  function cleanWorkspace(value) {
    const files = Array.isArray(value?.files) ? value.files.filter((file) => file && typeof file.path === "string").map((file) => ({
      id: String(file.id || file.path),
      path: normalizePath(file.path),
      content: String(file.content || ""),
      updatedAt: Number(file.updatedAt) || Date.now(),
      versions: Array.isArray(file.versions) ? file.versions.filter((version) => version && typeof version.content === "string").map((version) => ({
        id: String(version.id || version.createdAt || Date.now()),
        name: String(version.name || "Saved version").slice(0, 70),
        content: version.content,
        createdAt: Number(version.createdAt) || Date.now()
      })).slice(-40) : []
    })).filter((file, index, all) => file.path && all.findIndex((candidate) => candidate.path === file.path) === index) : [];
    return { name: String(value?.name || "Browser project").slice(0, 80), activeId: String(value?.activeId || files[0]?.id || ""), files };
  }

  root.DesktopcraftProjectsCore = { normalizePath, supportedFile, calculateLineDiff, cleanWorkspace };
})(typeof window === "undefined" ? globalThis : window);
