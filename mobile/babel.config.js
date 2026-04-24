module.exports = function (api) {
  api.cache(true);

  return {
    presets: [
      ['babel-preset-expo', { jsxImportSource: 'nativewind' }],
    ],
    plugins: [
      // MUST be last — Reanimated rewrites animation worklets at compile time
      'react-native-reanimated/plugin',
    ],
  };
};
