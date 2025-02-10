package io.antmedia.app;

import java.util.List;
import com.sun.jna.Structure.*;
import com.sun.jna.Structure;

import java.util.Arrays;

@FieldOrder({ "streamId", "pipeline_type", "pipeline", "protocol", "port_number", "hostname", "language" })
public class DataMaper extends Structure {
  public static class ByReference extends DataMaper implements Structure.ByReference {
  }

  public String streamId;
  public String pipeline_type;
  public String pipeline;
  public String protocol;
  public String port_number;
  public String hostname;
  public String language;

  @Override
  protected List<String> getFieldOrder() {
    return Arrays.asList(
        new String[] { "streamId", "pipeline_type", "pipeline", "protocol", "port_number", "hostname", "language" });
  }
}
