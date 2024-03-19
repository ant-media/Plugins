import sys
import PIL.Image as Image
import pathlib
import cv2

def faceDetection():
    imagePath = f'/usr/local/antmedia/image/{output_name}.png'

    img = cv2.imread(imagePath)

    gray_image = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    face_classifier = cv2.CascadeClassifier(
        cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
    )

    face = face_classifier.detectMultiScale(
        gray_image, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40)
    )

    for (x, y, w, h) in face:
        cv2.rectangle(img, (x, y), (x + w, y + h), (0, 255, 0), 4)

    img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

    # save into png file
    cv2.imwrite(f'/usr/local/antmedia/image/{output_name}_face_detected.png', img_rgb)

width = int(sys.argv[1])
height = int(sys.argv[2])
output_name = sys.argv[3]

frame_data = sys.stdin.buffer.read()
pathlib.Path('/usr/local/antmedia/image').mkdir(parents=True, exist_ok=True)

img = Image.frombuffer("RGBA", (width, height), frame_data, "raw", "RGBA", 0, 1)
img.save(f"/usr/local/antmedia/image/{output_name}.png")

faceDetection()
