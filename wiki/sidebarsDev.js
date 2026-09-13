// Dev track sidebar. Ids resolve against dev/, and only against dev/ — see sidebars.js.

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  dev: [
    'intro',
    'ecs-architecture',
    'build-environment',
    {
      type: 'category',
      label: 'Systems',
      items: [
        'systems/routine-ai',
        'systems/furniture-registry',
        'systems/hunger-and-sleep',
        'systems/houses',
        'systems/villages',
        'systems/persistence',
      ],
    },
    {
      type: 'category',
      label: 'Recipes',
      items: [
        'recipes/add-a-job',
        'recipes/add-a-hobby',
        'recipes/add-a-command',
      ],
    },
    'lessons-learned',
    'status',
    'experiments',
  ],
};

module.exports = sidebars;
