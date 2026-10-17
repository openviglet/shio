// SH676: a deterministic ZIP writer, stored, with no dependencies.
//
// ## Why hand-rolled rather than a package
//
// This workspace has no zip library and the site build has no runtime dependencies at
// all: every script here reads something the repo already owns and writes a derived
// artefact. Adding `archiver` to produce six small archives would be the largest
// dependency decision in this package, made for the least code.
//
// A *stored* zip (method 0, no compression) is the part of the format that is genuinely
// simple: a local header per entry, a central directory, an end record. There is no
// deflate stream to get wrong. The saving compression would buy is irrelevant: these
// packages are 50-110 KB of source text and they are downloaded once.
//
// ## Deterministic, because a published artefact should be checkable (P5)
//
// The archives are **not** committed (they are built by the GitHub Pages workflow and
// gitignored), so the reason is not diff noise. It is that a package somebody downloads
// from shio.viglet.org should be reproducible from the commit it was built at: rebuild
// the same tree, get the same bytes, and a mismatch means something other than the
// packages changed. A zip that embedded the build clock cannot make that promise, and it
// is the promise that makes "verify what you installed" possible at all.
//
// So every timestamp here is the DOS epoch, entries are written in the order given, and
// the same input is the same bytes.
//
// ## What it deliberately does not do
//
// No zip64 (a package this size cannot reach 4 GB), no encryption, no data descriptors,
// no directory entries: a reader creates a parent directory when it meets `a/b.txt`,
// which is what both `ShMarketplaceInstaller` and Python's `zipfile` do.

import { crc32 } from "node:zlib";

/** 1980-01-01 00:00:00, the earliest the DOS format can express. */
const DOS_DATE = 0x0021;
const DOS_TIME = 0x0000;

const LOCAL_SIGNATURE = 0x04034b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const END_SIGNATURE = 0x06054b50;

/** Version 2.0: what "stored, no zip64" needs and nothing more. */
const VERSION = 20;

/** Bit 11: the name is UTF-8. Set always, so a non-ASCII path is never mojibake. */
const FLAG_UTF8 = 0x0800;

/** Stored. */
const METHOD = 0;

/**
 * A zip of the given entries, as a Buffer.
 *
 * @param {Array<{name: string, body: Buffer|string}>} entries in the order they are written;
 *   `name` is the path inside the archive, always with forward slashes
 * @returns {Buffer}
 */
export function zip(entries) {
  const locals = [];
  const centrals = [];
  let offset = 0;

  for (const entry of entries) {
    const name = Buffer.from(entry.name, "utf8");
    const body = Buffer.isBuffer(entry.body)
      ? entry.body
      : Buffer.from(entry.body, "utf8");
    const sum = crc32(body);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(LOCAL_SIGNATURE, 0);
    local.writeUInt16LE(VERSION, 4);
    local.writeUInt16LE(FLAG_UTF8, 6);
    local.writeUInt16LE(METHOD, 8);
    local.writeUInt16LE(DOS_TIME, 10);
    local.writeUInt16LE(DOS_DATE, 12);
    local.writeUInt32LE(sum, 14);
    local.writeUInt32LE(body.length, 18);
    local.writeUInt32LE(body.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    locals.push(local, name, body);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(CENTRAL_SIGNATURE, 0);
    central.writeUInt16LE(VERSION, 4);
    central.writeUInt16LE(VERSION, 6);
    central.writeUInt16LE(FLAG_UTF8, 8);
    central.writeUInt16LE(METHOD, 10);
    central.writeUInt16LE(DOS_TIME, 12);
    central.writeUInt16LE(DOS_DATE, 14);
    central.writeUInt32LE(sum, 16);
    central.writeUInt32LE(body.length, 20);
    central.writeUInt32LE(body.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt16LE(0, 30); // extra
    central.writeUInt16LE(0, 32); // comment
    central.writeUInt16LE(0, 34); // disk
    central.writeUInt16LE(0, 36); // internal attrs
    // External attributes: a regular file, 0644, in the high word. Shifted with `>>> 0` because
    // `<<` in JavaScript yields a SIGNED int32 and 0o100644 << 16 is negative, which
    // `writeUInt32LE` rejects: the one arithmetic trap in this whole format.
    central.writeUInt32LE((0o100644 << 16) >>> 0, 38);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, name);

    offset += local.length + name.length + body.length;
  }

  const directory = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(END_SIGNATURE, 0);
  end.writeUInt16LE(0, 4); // this disk
  end.writeUInt16LE(0, 6); // disk with the directory
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  end.writeUInt16LE(0, 20); // comment

  return Buffer.concat([...locals, directory, end]);
}
