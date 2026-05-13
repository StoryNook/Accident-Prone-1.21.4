import { test } from "node:test";
import assert from "node:assert/strict";
import { unified } from "unified";
import remarkParse from "remark-parse";
import remarkStringify from "remark-stringify";
import { rewriteRepoLinks } from "./remark-rewrite-repo-links.mjs";

const REPO_BASE = "https://github.com/StoryNook/Accident-Prone-1.21.4/blob/main";

async function transform(markdown) {
  const file = await unified()
    .use(remarkParse)
    .use(rewriteRepoLinks, {
      repoBlobBase: REPO_BASE,
      collectionPaths: ["wiki/", "membership-setup.md", "security/hardening.md", "getting-started.md", "index.md"],
    })
    .use(remarkStringify)
    .process(markdown);
  return String(file);
}

test("rewrites a docs/superpowers path to a GitHub blob URL", async () => {
  const out = await transform("see [the spec](docs/superpowers/specs/foo.md)");
  assert.match(out, /\(https:\/\/github\.com\/StoryNook\/Accident-Prone-1\.21\.4\/blob\/main\/docs\/superpowers\/specs\/foo\.md\)/);
});

test("rewrites a src/ path to a GitHub blob URL", async () => {
  const out = await transform("see [source](src/main/java/com/storynook/Plugin.java)");
  assert.match(out, /\(https:\/\/github\.com\/StoryNook\/Accident-Prone-1\.21\.4\/blob\/main\/src\/main\/java\/com\/storynook\/Plugin\.java\)/);
});

test("rewrites a tools/ path to a GitHub blob URL", async () => {
  const out = await transform("see [the tool](tools/migrate_to_1_21_4/README.md)");
  assert.match(out, /\(https:\/\/github\.com\/StoryNook\/Accident-Prone-1\.21\.4\/blob\/main\/tools\/migrate_to_1_21_4\/README\.md\)/);
});

test("does NOT rewrite an in-collection sibling link", async () => {
  const out = await transform("see [Nanny](nanny.md)");
  assert.match(out, /\(nanny\.md\)/);
});

test("does NOT rewrite an in-collection link relative via docs/", async () => {
  const out = await transform("see [Nanny](docs/wiki/nanny.md)");
  assert.match(out, /\(docs\/wiki\/nanny\.md\)/);
});

test("does NOT rewrite an absolute external URL", async () => {
  const out = await transform("see [Astro](https://astro.build)");
  assert.match(out, /\(https:\/\/astro\.build\)/);
});

test("does NOT rewrite a fragment-only link", async () => {
  const out = await transform("see [section](#somewhere)");
  assert.match(out, /\(#somewhere\)/);
});
