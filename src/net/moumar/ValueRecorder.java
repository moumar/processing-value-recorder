package net.moumar.valuerecorder;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import processing.core.*;

public class ValueRecorder {
  public boolean isRecording = false;
  public boolean isPlaying = false;
  public boolean isLooping = false;
  private String current_event = null;
  private PApplet parent;
  private ArrayList <String> variable_names_to_record;
  private BufferedReader input;
  private PrintWriter output;
  private int started_at_millis = 0;
  private int started_at_frame = 0;
  private int framerate;
  private Method method_played = null, method_stopped = null, method_record_event = null;
  private String fullPath;
  String previous_values_list[];
  private boolean must_trigger_record_event = false;
  private boolean debug_mode = true;
  int next_event_ms = 0;
  
  public ValueRecorder(PApplet parent, int framerate, String[] vals) {
    this(parent, framerate, vals, "record.txt");
  }

  public ValueRecorder(PApplet parent, int framerate, String[] vals, String path) {
    this.parent = parent;
    //this.parent.registerPre(this);
    this.parent.registerDraw(this);

    fullPath = parent.dataPath(path);
    variable_names_to_record = new ArrayList <String> ();
    for(int i=0; i < vals.length; i++) {
      variable_names_to_record.add(vals[i]);
    }
    this.framerate = framerate;
    try {
      this.method_played = parent.getClass().getMethod("valueRecorderPlayed", new Class[] { ValueRecorder.class });
    } catch(NoSuchMethodException e) {
      // no such method, or an error.. which is fine, just ignore
    }
    try {
      this.method_record_event = parent.getClass().getMethod("valueRecorderEvent", new Class[] { ValueRecorder.class });
    } catch(NoSuchMethodException e) {
      // no such method, or an error.. which is fine, just ignore
    }
    try {
      this.method_stopped = parent.getClass().getMethod("valueRecorderStopped", new Class[] { ValueRecorder.class });
    } catch(NoSuchMethodException e) {
      // no such method, or an error.. which is fine, just ignore
    }
  }

  public void draw() {
    if (isPlaying) {
      restore_values(); 
    } else if (isRecording) {
      save_values();
    } 
  }
  
  public void record() {
    try {
      stop(false);
      this.started_at_millis = parent.millis();
      this.started_at_frame = parent.frameCount;
      isRecording = true;
      output = new PrintWriter(new FileOutputStream(fullPath));
      save_values(0);
      log("start recording");
    } catch(IOException e) {
      e.printStackTrace();
    }
  }
  
  public void stop(boolean send_stop_event) {
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
    if(send_stop_event && method_stopped != null) {
      try {
        method_stopped.invoke(parent, new Object[] { this });
      } catch(Exception e) {
        System.err.println("\nValueRecorder Warning: Disabling valueRecorderStopped(ValueRecorder recorder) because an unkown exception was thrown and caught");
        e.printStackTrace();
        method_stopped = null;
      }
    }
  }

  public void stop() {
    stop(true);
  }

  public void play() {
    stop(false);
    this.started_at_millis = parent.millis();
    this.started_at_frame = parent.frameCount;
    try {
      input = new BufferedReader(new FileReader(fullPath));
      isPlaying = true;
      restore_values();
      log("ValueRecorder played");
      if(method_played != null) {
        try {
          method_played.invoke(parent, new Object[] { this });
        } catch(Exception e) {
          System.err.println("\nValueRecorder Warning: Disabling valueRecorderPlayed(ValueRecorder recorder) because an unkown exception was thrown and caught");
          e.printStackTrace();
          method_played = null;
        }
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  public void playLoop() {
    play();
    isLooping = true;
  }

  public String currentEvent() {
    return current_event;
  }
  
  private void save_values() {
    save_values(relativeMillis());
  }

  private void save_values(int millis) {
    String values_list[] = new String[variable_names_to_record.size() * 2];
    for (int i = variable_names_to_record.size()-1; i >= 0; i--) { 
      String variable_name = (String)variable_names_to_record.get(i);
      values_list[i*2] = variable_name;
      values_list[i*2 + 1] = getStringValueOfField(variable_name);
    }
    String s = millis + "," + parent.join(values_list, ",");
    if (!Arrays.equals(values_list, previous_values_list)) {
      output.println(s);
    }
    previous_values_list = values_list;
  }

  private void restore_values() {
    try {
      boolean must_trigger_record_event = false;

      if(next_event_ms > relativeMillis())
        return;

      while(true) {
        String current_event = getNextLine();
        String[] next_values_list = current_event.split(",");
        next_event_ms = Integer.parseInt(next_values_list[0]);
        if (relativeMillis() < next_event_ms) {
          input.reset();
          break;
        }
        for(int i = 1; i < next_values_list.length; i+=2) {
          String variable_name = next_values_list[i];
          String variable_value = next_values_list[i+1];
          if (variable_name.endsWith("()")) {
            callFunction(variable_name, variable_value);
          } else {
            setValueOfField(variable_name, variable_value);
          }
          if(debug_mode) {
            parent.print("set " + variable_name + ": " + variable_value + ", ");
          }
          must_trigger_record_event = true;
        }

        if (debug_mode)  {
          parent.println("");
        }

        if (must_trigger_record_event && method_record_event != null) {
          try {
            method_record_event.invoke(parent, new Object[] { this });
          } catch(Exception e) {
            System.err.println("\nValueRecorder Warning: Disabling valueRecorderEvent(ValueRecorder recorder) because an unkown exception was thrown and caught");
            e.printStackTrace();
            method_record_event = null;
          }
        }
      }
    } catch(IOException e) {
      if (debug_mode)
        e.printStackTrace();

      if(isLooping) {
        log("ValueRecorder looped");
        play();
      } else {
        stop();
      }
    }
  }

  private String getNextLine() throws IOException {
    String line;
    while(true) {
      input.mark(4096);
      line = input.readLine();
      log("line read: '" + line + "'");
      if (line == null)
        throw(new EOFException()); 
      if(!line.startsWith("#")) {
        break;
      }
    }
    return line;
  }

  private int relativeMillis() {
    if(isPlaying) {
      return (new Float( (parent.frameCount - this.started_at_frame) * 1000f/this.framerate ).intValue());
    } else {
      return parent.millis() - this.started_at_millis;
    }
  }
  
  private void callFunction(String function_name, String s) {
    try {
      Class<?> targetClass = parent.getClass();
      Method targetMethod = targetClass.getDeclaredMethod(function_name, new Class[] { String.class });
      targetMethod.invoke(parent, new Object[] { s });
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  private void setValueOfField(String variable_name, String s) {
    try {
      Class<?> targetClass = parent.getClass();
      Field targetField = targetClass.getDeclaredField(variable_name);
      Class<?> targetObjectFieldType = targetField.getType();
      if (targetObjectFieldType == Float.TYPE) {
        targetField.setFloat(parent, Float.parseFloat(s));
      } else if (targetObjectFieldType == Integer.TYPE) {
        targetField.setInt(parent, Integer.parseInt(s));
      }
    } catch(NoSuchFieldException e) {
    } catch (IllegalAccessException e) {
    }
  }

  private String getStringValueOfField(String variable_name) {
    try {
      Class<?> targetClass = parent.getClass();
      Field targetField = targetClass.getDeclaredField(variable_name);
      Class<?> targetObjectFieldType = targetField.getType();
      
      if (targetObjectFieldType == Float.TYPE) {
        return new Float(targetField.getFloat(parent)).toString();
      } else if (targetObjectFieldType == Integer.TYPE) {
        return new Integer(targetField.getInt(parent)).toString();
      }
    } catch(NoSuchFieldException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return "0";
  }

  private void log(String s) {
    if(debug_mode) {
      parent.println(s);
    }
  }
}
