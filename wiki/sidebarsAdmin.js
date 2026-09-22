// Admin track sidebar. Ids resolve against admin/, and only against admin/ — see sidebars.js.

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  admin: [
    'intro',
    'commands',
    'generative-ai',
    'balancing',
    'troubleshooting',
  ],
};

module.exports = sidebars;
