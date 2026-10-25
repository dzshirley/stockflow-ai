import { readFile, readdir, stat } from "node:fs/promises";
import { basename } from "node:path";
import process from "node:process";
import { URL } from "node:url";

const DIST_DIR = new URL("../dist/", import.meta.url);
const INITIAL_LIMIT_BYTES = 300 * 1024;
const CHUNK_LIMIT_BYTES = 400 * 1024;

const html = await readFile(new URL("index.html", DIST_DIR), "utf8");
const initialMatch = html.match(/<script[^>]+src="\/assets\/([^"]+\.js)"/);

if (!initialMatch) {
  throw new Error("Unable to identify the initial JavaScript entry in dist/index.html");
}

const assetsDir = new URL("assets/", DIST_DIR);
const javascriptFiles = (await readdir(assetsDir)).filter((file) => file.endsWith(".js"));
const sizes = await Promise.all(
  javascriptFiles.map(async (file) => ({ file, bytes: (await stat(new URL(file, assetsDir))).size })),
);
const initial = sizes.find(({ file }) => file === basename(initialMatch[1]));
const largest = sizes.reduce((current, candidate) =>
  candidate.bytes > current.bytes ? candidate : current,
);

if (!initial) {
  throw new Error(`Initial bundle ${initialMatch[1]} was not found in dist/assets`);
}

process.stdout.write(
  `Bundle budget: initial=${initial.bytes} bytes (${initial.file}), largest=${largest.bytes} bytes (${largest.file})\n`,
);

if (initial.bytes > INITIAL_LIMIT_BYTES) {
  throw new Error(`Initial JavaScript exceeds ${INITIAL_LIMIT_BYTES} bytes`);
}
if (largest.bytes > CHUNK_LIMIT_BYTES) {
  throw new Error(`JavaScript chunk exceeds ${CHUNK_LIMIT_BYTES} bytes`);
}
