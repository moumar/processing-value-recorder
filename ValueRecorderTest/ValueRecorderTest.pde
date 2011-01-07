ValueRecorder recorder ;
int x = 0, y = 0;
final int FRAMERATE = 30;

void setup() {
  size(200, 200);
  String[] values_to_record = {"x", "y"};
  recorder = new ValueRecorder(this, FRAMERATE, values_to_record);
  stroke(0);
  fill(0);
}

void draw() {
  //background(255);
  fill(0);
  ellipse(x, y, 5, 5);
}

void mousePressed() {
  if (recorder.isRecording) {
    recorder.play();
  } else {
    recorder.record();
  }
}

void valueRecorderPlayed(ValueRecorder recorder) {
  // clearing screen
  fill(255);
  rect(0, 0, width, height);
  println("played");
}

void mouseMoved() {
  if (recorder.isRecording) {
    x = mouseX;
    y = mouseY;
  }
}
