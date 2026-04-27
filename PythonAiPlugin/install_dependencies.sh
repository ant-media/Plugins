#ubuntu
echo "Updating package lists..."
sudo apt update

echo "Installing system packages..."
sudo apt install -y \
  python3-venv \
  openjdk-17-jdk-headless \
  maven \
  python3-pip \
  python3-dev \
  python3-numpy \
  ffmpeg \
  cmake \
  libavformat-dev

echo "✅ All dependencies installed successfully."
