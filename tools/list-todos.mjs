import { execFileSync } from 'node:child_process';
import { lstatSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const marker = /\bTODO\b/i;
const excluded = /(^|\/)(generated|node_modules|dist|build|coverage)\//;
const lockfile =
  /(^|\/)(pnpm-lock\.yaml|package-lock\.json|yarn\.lock|bun\.lockb?)$/;

function listTodos() {
  // Git supplies tracked and untracked paths while respecting ignore rules.
  const paths = execFileSync(
    'git',
    ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
    { cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 },
  );
  let total = 0;
  let files = 0;

  for (const path of [...new Set(paths.split('\0').filter(Boolean))].sort()) {
    if (
      excluded.test(path) ||
      lockfile.test(path) ||
      path === 'tools/list-todos.mjs'
    ) {
      continue;
    }

    let content;
    try {
      const absolute = resolve(root, path);
      // Do not follow symlinks, including links outside the repository.
      if (!lstatSync(absolute).isFile()) continue;
      content = readFileSync(absolute);
    } catch (error) {
      if (error.code === 'ENOENT') continue; // Tracked files deleted locally.
      throw error;
    }
    if (content.includes(0)) continue; // Binary file.

    const lines = content.toString('utf8').split(/\r?\n/);
    const entries = [];
    for (let index = 0; index < lines.length; index++) {
      const line = lines[index];
      if (!marker.test(line)) continue;
      const start = index + 1;
      const text = [line.slice(line.search(marker)).trim()];
      // Preserve wrapped comments without including code.
      const comment = line.match(/^\s*(\/\/|\*|\/\*|<!--)/)?.[1];
      const continuation =
        comment === '//'
          ? /^\s*\/\/\s?(.*)$/
          : comment === '*' || comment === '/*'
            ? /^\s*\*(?!\/)\s?(.*)$/
            : comment === '<!--'
              ? /^\s*(.*)$/
              : null;
      while (continuation && index + 1 < lines.length) {
        if (/\*\/|-->/.test(lines[index])) break;
        const next = lines[index + 1].match(continuation);
        if (!next || !next[1].trim() || marker.test(next[1])) break;
        text.push(next[1].trim());
        index++;
      }
      entries.push({
        line: start,
        text: text.join(' ').replace(/\s*(\*\/|-->)$/, ''),
      });
    }
    if (!entries.length) continue;

    files++;
    total += entries.length;
    console.log(`\n${path} (${entries.length})`);
    const width = String(entries.at(-1).line).length;
    for (const entry of entries) {
      const prefix = `  ${String(entry.line).padStart(width)} | `;
      const indent = ' '.repeat(prefix.length);
      let output = prefix;
      for (const word of entry.text.split(/\s+/)) {
        if (
          output.length > prefix.length &&
          output.length + word.length > 100
        ) {
          console.log(output.trimEnd());
          output = indent;
        }
        output += `${word} `;
      }
      console.log(output.trimEnd());
    }
  }

  console.log(
    total
      ? `\n${total} TODO entries in ${files} files.\n`
      : 'No TODO markers found.\n',
  );
}

try {
  listTodos();
} catch (error) {
  console.error(`Unable to list TODO markers: ${error.message}`);
  process.exitCode = 1;
}
