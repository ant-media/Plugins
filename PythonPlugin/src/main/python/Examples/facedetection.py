import cv2
import os


def onVideoFrame(rgb_image):
    gray_image = cv2.cvtColor(rgb_image, cv2.COLOR_RGB2GRAY)

    model_path = os.path.dirname(os.path.abspath(__file__))
    model_path = os.path.join(
        model_path, "haarcascade_frontalface_default.xml")

    face_classifier = cv2.CascadeClassifier(
        model_path
    )
    face = face_classifier.detectMultiScale(
        gray_image, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40)
    )
    for (x, y, w, h) in face:
        cv2.rectangle(rgb_image, (x, y), (x + w, y + h), (0, 255, 0), 4)
