import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ValueRecorder {
  boolean isRecording = false;
  boolean isPlaying = false;
  boolean isLooping = false;
  private PApplet app;
  private ArrayList variable_names_to_record;
  private BufferedReader input;
  private PrintWriter output;
  private int started_at_millis = 0;
  private int started_at_frame = 0;
  String previous_values_list[];
  String next_values_list[] = null; 
  private int framerate;
  private Method method_played, method_stopped;
  
  ValueRecorder(PApplet app, int framerate, String[] vals) {
    this.app = app;
    this.app.registerDraw(this);
    variable_names_to_record = new ArrayList();
    for(int i=0; i < vals.length; i++) {
      variable_names_to_record.add(vals[i]);
    }
    this.framerate = framerate;
    try {
      this.method_played = app.getClass().getMethod("valueRecorderPlayed", new Class[] { ValueRecorder.class });
    } catch(NoSuchMethodException e) {
      // no such method, or an error.. which is fine, just ignore
    }
  }

  void draw() {
    if (isRecording) {
      save_values();
    } else if (isPlaying) {
      restore_values(); 
    }
  }
  
  void record() {
    stop();
    this.started_at_millis = millis();
    this.started_at_frame = frameCount;
    output = createWriter("record.txt"); 
    isRecording = true;
    save_values(0);
    log("start recording");
  }
  
  void stop() {
    if (isRecording) {
      save_values();
      output.flush();
      output.close();
    } 
    if (isPlaying) {
      try {
        input.close();
      } catch(IOException e) {
      }
    }
    isPlaying = isRecording = false; 
    log("ValueRecorder stopped");
  }

  void play() {
    stop();
    this.started_at_millis = millis();
    this.started_at_frame = frameCount;
    input = createReader("record.txt");
    isPlaying = true;
    restore_values();
    log("ValueRecorder played");
    if(method_played != null) {
      try {
        method_played.invoke(this.app, new Object[] { this });
      } catch(Exception e) {
        System.err.println("\nValueRecorder Warning: Disabling valueRecorderPlayed(ValueRecorder recorder) because an unkown exception was thrown and caught");
        e.printStackTrace();
        method_played = null;
      }
    }
  }

  void playLoop() {
    play();
    isLooping = true;
  }
  
  private void save_values() {
    save_values(relativeMillis());
  }

  private void save_values(int millis) {
    String values_list[] = new String[variable_names_to_record.size() * 2];
    for (int i = variable_names_to_record.size()-1; i >= 0; i--) { 
      String variable_name = (String)variable_names_to_record.get(i);
      values_list[i*2] = variable_name;
      values_list[i*2 + 1] = getValueOfField(variable_name).toString();
    }
    if (!Arrays.equals(values_list, previous_values_list)) {
      output.println(millis + "," + join(values_list, ","));
    }
    previous_values_list = values_list;
  }

  private void restore_values() {
    try {
      if(next_values_list == null) {
        next_values_list = getNextValues();
      } 
      while(!next_event_is_in_the_future()) {
        for(int i = 1; i < next_values_list.length; i+=2) {
          String variable_name = next_values_list[i];
          String variable_value = next_values_list[i+1];
          setValueOfField(variable_name, variable_value);
          print("set " + variable_name + ": " + variable_value + ", ");
        }
        println("");

        next_values_list = getNextValues();
      }
    } catch(IOException e) {
      if(isLooping) {
        log("ValueRecorder looped");
        play();
      } else {
        stop();
      }
    }
  }

  private String[] getNextValues() throws EOFException {
    try {
      String line = input.readLine();
      if (line == null)
        throw(new EOFException()); 
      return split(line, ",");
    } catch(IOException e) {
      throw(new EOFException()); 
    }
  }

  private boolean next_event_is_in_the_future() {
    return relativeMillis() < int(next_values_list[0]);
  }

  private int relativeMillis() {
    if(isPlaying) {
      return int( (frameCount - this.started_at_frame) * 1000f/this.framerate );
    } else {
      return millis() - this.started_at_millis;
    }
  }
  
  private void setValueOfField(String variable_name, String s) {
    try {
      Class<?> targetClass = app.getClass();
      Field targetField = targetClass.getDeclaredField(variable_name);
      Class<?> targetObjectFieldType = targetField.getType();
      if (targetObjectFieldType == Float.TYPE) {
        targetField.setFloat(this.app, float(s));
      } else if (targetObjectFieldType == Integer.TYPE) {
        targetField.setInt(this.app, int(s));
      }
    } catch(NoSuchFieldException e) {
    } catch (IllegalAccessException e) {
    }
  }

  private Number getValueOfField(String variable_name) {
    try {
      Class<?> targetClass = app.getClass();
      Field targetField = targetClass.getDeclaredField(variable_name);
      Class<?> targetObjectFieldType = targetField.getType();
      
      if (targetObjectFieldType == Float.TYPE) {
        return targetField.getFloat(this.app);
      } else if (targetObjectFieldType == Integer.TYPE) {
        return targetField.getInt(this.app);
      }
    } catch(NoSuchFieldException e) {
    } catch (IllegalAccessException e) {
    }
    return 0f;
  }

  private void log(String s) {
    if(false) {
      println(s);
    }
  }
}
