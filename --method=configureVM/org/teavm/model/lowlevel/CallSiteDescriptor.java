package org.teavm.model.lowlevel;

import java.util.ArrayList;
import java.util.List;

public class CallSiteDescriptor {
   private int id;
   private List<ExceptionHandlerDescriptor> handlers = new ArrayList<>();
   private CallSiteLocation[] locations;

   public CallSiteDescriptor(int id, CallSiteLocation[] locations) {
      this.id = id;
      this.locations = locations != null ? (CallSiteLocation[])locations.clone() : null;
   }

   public int getId() {
      return this.id;
   }

   public CallSiteLocation[] getLocations() {
      return this.locations != null ? (CallSiteLocation[])this.locations.clone() : null;
   }

   public List<ExceptionHandlerDescriptor> getHandlers() {
      return this.handlers;
   }
}
