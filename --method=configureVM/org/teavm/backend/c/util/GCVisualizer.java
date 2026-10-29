package org.teavm.backend.c.util;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class GCVisualizer {
   private static final int WIDTH = 1024;
   private static final int LINE_HEIGHT = 4;
   private static final int GAP_SIZE = 1;
   private static final int GC_GAP_SIZE = 3;

   private GCVisualizer() {
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 2) {
         System.err.println("Two arguments (input, output) expected");
         System.exit(1);
      }

      List<GCVisualizer.Line> lines = readData(args[0]);
      BufferedImage bitmap = createBitmap(lines);
      writeBitmap(bitmap, args[1]);
   }

   private static List<GCVisualizer.Line> readData(String fileName) throws IOException {
      List<GCVisualizer.Line> lines = new ArrayList<>();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8))) {
         while (true) {
            String fileLine = reader.readLine();
            if (fileLine == null) {
               return lines;
            }

            int charIndex = fileLine.indexOf(58);
            if (charIndex >= 0) {
               String kind = fileLine.substring(0, charIndex);
               if (kind.equals("start") || lines.isEmpty()) {
                  lines.add(new GCVisualizer.Line());
               }

               GCVisualizer.Line line = lines.get(lines.size() - 1);
               int[] array;
               switch (kind) {
                  case "start":
                     array = line.start;
                     break;
                  case "sweep":
                     array = line.sweep;
                     break;
                  case "defrag":
                     array = line.defrag;
                     break;
                  default:
                     continue;
               }

               charIndex = fileLine.indexOf(32, charIndex);
               if (charIndex >= 0) {
                  charIndex++;

                  for (int i = 0; i < array.length; i++) {
                     int next = fileLine.indexOf(32, charIndex);
                     if (next < 0) {
                        next = fileLine.length();
                     }

                     try {
                        array[i] = Integer.parseInt(fileLine.substring(charIndex, next));
                     } catch (NumberFormatException var12) {
                     }

                     if (next == fileLine.length()) {
                        break;
                     }

                     charIndex = next + 1;
                  }
               }
            }
         }
      }
   }

   private static BufferedImage createBitmap(List<GCVisualizer.Line> lines) {
      int height = lines.size() * 18;
      int[] data = new int[1024 * height];
      int offset = 0;

      for (GCVisualizer.Line line : lines) {
         offset = renderComponent(data, line.start, offset, 0, 0, 255);
         offset = renderComponent(data, line.sweep, offset, 0, 255, 0);
         offset = renderComponent(data, line.defrag, offset, 255, 0, 0);
         renderLine(data, offset, 3, 0, 0, 0);
         offset += 3072;
      }

      BufferedImage image = new BufferedImage(1024, height, 2);
      image.setRGB(0, 0, 1024, height, data, 0, 1024);
      return image;
   }

   private static int renderComponent(int[] data, int[] array, int offset, int r, int g, int b) {
      renderArray(data, array, offset, r, g, b);
      offset += 4096;
      renderLine(data, offset, 1, 0, 0, 0);
      return offset + 1024;
   }

   private static void renderArray(int[] data, int[] array, int offset, int r, int g, int b) {
      for (int i = 0; i < 1024; i++) {
         int start = array.length * i / 1024;
         int end = array.length * (i + 1) / 1024;
         int total = 0;

         for (int j = start; j < end; j++) {
            total += array[j];
         }

         double rate = total / (4096.0 * (end - start));
         int pixelR = makeColorComponent(r, rate);
         int pixelG = makeColorComponent(g, rate);
         int pixelB = makeColorComponent(b, rate);
         int pixelOffset = offset;
         int pixel = 0xFF000000 | (pixelR & 0xFF) << 16 | (pixelG & 0xFF) << 8 | pixelB & 0xFF;

         for (int j = 0; j < 4; j++) {
            data[pixelOffset] = pixel;
            pixelOffset += 1024;
         }

         offset++;
      }
   }

   private static void renderLine(int[] data, int offset, int height, int r, int g, int b) {
      int count = height * 1024;
      int pixel = 0xFF000000 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;

      for (int i = 0; i < count; i++) {
         data[offset++] = pixel;
      }
   }

   private static int makeColorComponent(int c, double rate) {
      int r = (int)(c * rate + 255.0 * (1.0 - rate));
      return Math.min(Math.max(r, 0), 255);
   }

   private static void writeBitmap(BufferedImage image, String fileName) throws IOException {
      ImageIO.write(image, "png", new File(fileName));
   }

   static class Line {
      int[] start = new int[4096];
      int[] sweep = new int[4096];
      int[] defrag = new int[4096];
   }
}
