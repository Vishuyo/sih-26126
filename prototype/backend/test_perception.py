from perception import YOLODetector
d = YOLODetector()
print('Model loaded:', d.model.model.model[-1].nc, 'classes')