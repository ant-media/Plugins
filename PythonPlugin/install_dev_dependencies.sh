#ubuntu
echo "Updating package lists..."
sudo apt update

echo "Installing system packages..."
sudo apt install -y \
  openjdk-17-jdk-headless \
  maven \
  python3-pip \
  python3-dev \
  python3-numpy \
  ffmpeg \
  cmake \
  libavformat-dev

echo "Installing Python packages..."
sudo pip install \
  cython \
  opencv-python

echo "✅ All dependencies installed successfully."
