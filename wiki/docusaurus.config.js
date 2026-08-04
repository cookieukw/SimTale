// @ts-check
// SimTale wiki configuration.
//
// Three independent tracks (player, admin, dev) instead of one tree: the audiences do not mix, and
// a player looking up "why won't my NPC sleep" should never trip over ECS.

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'SimTale',
  tagline: 'Social simulation for Hytale',
  favicon: 'img/favicon.ico',

  // Adjust when publishing: url is the domain, baseUrl is the path within it.
  url: 'https://cookieukw.github.io',
  baseUrl: '/SimTale/',

  organizationName: 'cookieukw',
  projectName: 'SimTale',

  // A broken link fails the build on purpose. A wiki with dead links is worse than an incomplete one.
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    // English is the wiki language. pt-BR can be added later without restructuring.
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          // Player track, at the site root.
          sidebarPath: require.resolve('./sidebars.js'),
          routeBasePath: '/',
          editUrl: 'https://github.com/cookieukw/SimTale/tree/main/wiki/',
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'admin',
        path: 'admin',
        routeBasePath: 'admin',
        sidebarPath: require.resolve('./sidebars.js'),
      },
    ],
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'dev',
        path: 'dev',
        routeBasePath: 'dev',
        sidebarPath: require.resolve('./sidebars.js'),
      },
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      colorMode: {
        // The mod is played at night. Dark is the sensible default.
        defaultMode: 'dark',
        respectPrefersColorScheme: true,
      },
      navbar: {
        title: 'SimTale',
        items: [
          { type: 'docSidebar', sidebarId: 'player', position: 'left', label: 'Player' },
          { to: '/admin/intro', position: 'left', label: 'Server' },
          { to: '/dev/intro', position: 'left', label: 'Developer' },
          {
            href: 'https://github.com/cookieukw/SimTale',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        copyright: `SimTale — a social simulation mod for Hytale. Documentation in progress.`,
      },
    }),
};

module.exports = config;
