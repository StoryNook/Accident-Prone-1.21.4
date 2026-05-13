import { defineCollection } from "astro:content";
import { docsSchema } from "@astrojs/starlight/schema";
import { glob } from "astro/loaders";

export const collections = {
  docs: defineCollection({
    loader: glob({
      pattern: [
        "wiki/*.md",
        "membership-setup.md",
        "security/hardening.md",
        "getting-started.md",
        "index.md",
      ],
      base: "../docs",
    }),
    schema: docsSchema(),
  }),
};
