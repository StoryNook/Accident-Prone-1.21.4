import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import { rewriteRepoLinks } from "./src/plugins/remark-rewrite-repo-links.mjs";

export default defineConfig({
  site: "https://storynook.github.io",
  base: "/Accident-Prone-1.21.4",
  trailingSlash: "ignore",
  markdown: {
    remarkPlugins: [
      [
        rewriteRepoLinks,
        {
          repoBlobBase: "https://github.com/StoryNook/Accident-Prone-1.21.4/blob/main",
          collectionPaths: [
            "wiki/",
            "membership-setup.md",
            "security/hardening.md",
            "getting-started.md",
            "index.md",
          ],
        },
      ],
    ],
  },
  integrations: [
    starlight({
      title: "Accident-Prone",
      description: "Reference documentation for the Accident-Prone Minecraft plugin.",
      customCss: ["./src/styles/oath-theme.css"],
      favicon: "/favicon.svg",
      components: {
        SiteTitle: "./src/components/SiteTitle.astro",
      },
      social: [
        {
          icon: "github",
          label: "GitHub",
          href: "https://github.com/StoryNook/Accident-Prone-1.21.4",
        },
      ],
      sidebar: [
        { label: "Overview", link: "/" },
        {
          label: "Getting started",
          items: [{ label: "Install & first run", link: "/getting-started" }],
        },
        {
          label: "Subsystems",
          items: [
            { label: "Nanny NPCs", link: "/wiki/nanny" },
            { label: "Hypnosis", link: "/wiki/hypnosis" },
            { label: "Toilet & warnings", link: "/wiki/toilet-warnings" },
            { label: "Rash", link: "/wiki/rash" },
          ],
        },
        {
          label: "Reference",
          items: [
            { label: "Design Registry", link: "/wiki/design-registry" },
            { label: "Resource pack (1.21.4)", link: "/wiki/resource-pack-1-21-4" },
            { label: "Plugin dependencies", link: "/wiki/dependencies" },
            { label: "Integrations", link: "/wiki/integrations" },
          ],
        },
        {
          label: "Admin",
          items: [
            { label: "Membership setup", link: "/membership-setup" },
            { label: "Security hardening", link: "/security/hardening" },
          ],
        },
      ],
    }),
  ],
});
