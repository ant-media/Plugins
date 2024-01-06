const path = require('path');

module.exports = {
  entry: './src/publisher.js',
  output: {
    filename: 'publisher.js',
    path: path.resolve('../resources/'),
  }
};
