# place the below code in libPythonWrapper.pyx file  in onVideoFrame function in betweeen where it says start writing code from here

gray_image = cv2.cvtColor(rgb_image, cv2.COLOR_RGB2GRAY)
face_classifier = cv2.CascadeClassifier(
    "haarcascade_frontalface_default.xml"
)
face = face_classifier.detectMultiScale(
    gray_image, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40)
)
for (x, y, w, h) in face:
    cv2.rectangle(rgb_image, (x, y), (x + w, y + h), (0, 255, 0), 4)
