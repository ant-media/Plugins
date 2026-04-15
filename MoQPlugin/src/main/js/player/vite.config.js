export default {
  base: "./",
  build: {
    rollupOptions: {
      input: {
        index: "index.html",
        play: "play.html",
        publish: "publish.html",
      },
    },
  },
};
