#!/usr/bin/env node
// Genera core/maps/src/main/assets/map-layers-{light,dark}.json a partir de
// @protomaps/basemaps (paquete npm oficial de Protomaps para MapLibre GL).
//
// La app NO depende de Node: este script corre one-shot en dev, sus salidas
// (los dos JSON de assets) van commiteadas al repo.
//
// Re-ejecutar:
//   node scripts/gen-map-layers.mjs
//
// El paquete se instala en un directorio temporal FUERA del árbol del repo
// (os.tmpdir()), nunca como package.json/node_modules commiteado. Si ya está
// instalado ahí de una corrida anterior, no se reinstala; para forzar
// reinstalación (p.ej. al subir PACKAGE_VERSION):
//   PMTILES_GEN_FORCE_INSTALL=1 node scripts/gen-map-layers.mjs

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const PACKAGE_NAME = "@protomaps/basemaps";
const PACKAGE_VERSION = "5.7.2";
const LANGUAGE = "es";
const TILES_SOURCE_NAME = "protomaps";
const THEMES = ["light", "dark"];

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = dirname(scriptDir);
const assetsDir = join(repoRoot, "core", "maps", "src", "main", "assets");
const installDir = join(tmpdir(), "obd2-gen-map-layers", PACKAGE_VERSION);

function packageEntryPath() {
  return join(installDir, "node_modules", "@protomaps", "basemaps", "dist", "esm", "index.js");
}

function ensurePackageInstalled() {
  const entry = packageEntryPath();
  if (existsSync(entry) && process.env.PMTILES_GEN_FORCE_INSTALL !== "1") {
    return entry;
  }
  mkdirSync(installDir, { recursive: true });
  execFileSync(
    "npm",
    ["install", "--prefix", installDir, "--no-save", "--no-audit", "--no-fund", `${PACKAGE_NAME}@${PACKAGE_VERSION}`],
    // shell:true es necesario en Windows: npm.cmd es un batch file, no un PE,
    // y execFileSync sin shell falla con EINVAL al intentar spawnearlo directo.
    { stdio: "inherit", shell: process.platform === "win32" },
  );
  if (!existsSync(entry)) {
    throw new Error(`Instalación esperada en ${entry} pero no se encontró tras npm install`);
  }
  return entry;
}

async function loadBasemaps() {
  const entry = ensurePackageInstalled();
  return import(pathToFileURL(entry).href);
}

function writeLayersAsset(fileName, layerSpecs) {
  const target = join(assetsDir, fileName);
  const json = `${JSON.stringify(layerSpecs, null, 2)}\n`;
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, json, "utf8");
  return target;
}

function flavorFor(themeName, flavors) {
  return themeName === "dark" ? flavors.DARK : flavors.LIGHT;
}

async function main() {
  const basemaps = await loadBasemaps();
  for (const themeName of THEMES) {
    const flavor = flavorFor(themeName, basemaps);
    const layerSpecs = basemaps.layers(TILES_SOURCE_NAME, flavor, { lang: LANGUAGE });
    const target = writeLayersAsset(`map-layers-${themeName}.json`, layerSpecs);
    console.log(`${themeName}: ${layerSpecs.length} capas -> ${target}`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
