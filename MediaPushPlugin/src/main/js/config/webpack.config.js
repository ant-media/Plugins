const path = require('path');

module.exports = {
  entry: './src/media-push-publisher.js',
  output: {
    filename: 'media-push-publisher.js',
    path: path.resolve('../resources/'),
  }
};
