#ubuntu
sudo apt install openjdk-17-jdk-headless
sudo apt install maven
sudo apt install python3-pip
sudo apt-get install libavutil-dev libavfilter-dev libavformat-dev libsdl2-dev libavcodec-dev libx264-dev libxvidcore-dev libvdpau-dev libva-dev libxcb-shm0-dev libwavpack-dev libvpx-dev libvorbis-dev libogg-dev libvidstab-dev libspeex-dev libopus-dev libopencore-amrnb-dev libopencore-amrwb-dev libmp3lame-dev libfreetype6-dev libfdk-aac-dev libass-dev libbz2-dev libsoxr-dev
sudo at install ffmpeg
apt-get install libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev libgstreamer-plugins-bad1.0-dev gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-tools gstreamer1.0-x gstreamer1.0-alsa gstreamer1.0-gl gstreamer1.0-gtk3 gstreamer1.0-qt5 libjson-glib-dev gstreamer1.0-nice gstreamer1.0-pulseaudio 
sudo apt install cmake
sudo apt-get install python3-numpy

sudo add-apt-repository ppa:deadsnakes/ppa
sudo apt-get update
sudo apt-get install python3.13

sudo python3.13 -m pip3 install cython
sudo python3.13 -m pip3 uninstall opencv-python

# we need Gstreamer Support for OpenCV so build from src
git clone --branch 4.x --single-branch --recursive https://github.com/skvark/opencv-python.git
cd opencv-python
export CMAKE_ARGS="-DWITH_GSTREAMER=ON"
pip install --upgrade pip wheel
# this is the build step - the repo estimates it can take from 5 
#   mins to > 2 hrs depending on your computer hardware
pip wheel . --verbose
pip install opencv_python*.whl
