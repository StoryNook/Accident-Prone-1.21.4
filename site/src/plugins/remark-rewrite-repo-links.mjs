import { visit } from "unist-util-visit";

/**
 * Remark plugin that rewrites repo-relative markdown links pointing OUTSIDE
 * the content collection to absolute GitHub blob URLs.
 *
 * Options:
 *   repoBlobBase: base URL like "https://github.com/StoryNook/Accident-Prone-1.21.4/blob/main"
 *   collectionPaths: array of repo-relative paths that ARE in the collection
 *     (these stay untouched and Astro/Starlight handles resolution).
 *     Entries ending in "/" match by prefix; bare filenames match exactly.
 *
 * Skips:
 *   - absolute URLs (http://, https://, mailto:, etc.)
 *   - fragment-only links (#foo)
 *   - protocol-relative (//host/path)
 *   - site-absolute paths (/foo — already final in-site URLs)
 *   - links whose path resolves to an entry in collectionPaths
 *   - in-collection sibling links (relative paths with no leading directory)
 */
export function rewriteRepoLinks(options = {}) {
  const repoBlobBase = (options.repoBlobBase || "").replace(/\/+$/, "");
  const collectionPaths = options.collectionPaths || [];
  if (!repoBlobBase) {
    throw new Error("remark-rewrite-repo-links: repoBlobBase is required");
  }

  return (tree) => {
    visit(tree, "link", (node) => {
      const url = node.url || "";

      if (/^[a-z][a-z0-9+.-]*:/i.test(url)) return;
      if (url.startsWith("#")) return;
      if (url.startsWith("//")) return;
      if (url.startsWith("/")) return;

      const path = url.replace(/^\.\//, "");

      if (!path.includes("/")) return;

      const candidate = path.startsWith("docs/") ? path.slice(5) : path;
      const isInCollection = collectionPaths.some((p) =>
        p.endsWith("/") ? candidate.startsWith(p) : candidate === p
      );
      if (isInCollection) return;

      node.url = `${repoBlobBase}/${path}`;
    });
  };
}
