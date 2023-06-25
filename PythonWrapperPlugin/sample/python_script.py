import sys
import PIL.Image as Image
import pathlib

width = int(sys.argv[1])
height = int(sys.argv[2])
output_name = sys.argv[3]

frame_data = sys.stdin.buffer.read()
pathlib.Path('/usr/local/antmedia/image').mkdir(parents=True, exist_ok=True)

img = Image.frombuffer("RGBA", (width, height), frame_data, "raw", "RGBA", 0, 1)
img.save(f"/usr/local/antmedia/image/{output_name}.png")