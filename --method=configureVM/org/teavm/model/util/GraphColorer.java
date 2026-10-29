package org.teavm.model.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import org.teavm.common.Graph;

public class GraphColorer {
   public void colorize(Graph graph, int[] colors) {
      Object[] categories = new Object[graph.size()];
      Arrays.fill(categories, new Object());
      this.colorize(graph, colors, categories, new String[graph.size()]);
   }

   public void colorize(Graph graph, int[] colors, Object[] categories, String[] names) {
      ArrayList<Object> colorCategories = new ArrayList<>(graph.size());
      List<String> colorNames = new ArrayList<>();

      for (int i = 0; i < colors.length; i++) {
         int color = colors[i];
         if (colors[i] >= 0) {
            while (colorCategories.size() <= color) {
               colorCategories.add(-1);
            }

            while (colorNames.size() <= color) {
               colorNames.add(null);
            }

            colorCategories.set(color, categories[i]);
            colorNames.set(color, names[i]);
         }
      }

      BitSet usedColors = new BitSet();

      for (int v : this.getOrdering(graph)) {
         if (colors[v] < 0) {
            usedColors.clear();
            usedColors.set(0);

            for (int succ : graph.outgoingEdges(v)) {
               if (colors[succ] >= 0) {
                  usedColors.set(colors[succ]);
               }
            }

            int color = 0;

            while (true) {
               color = usedColors.nextClearBit(color);

               while (colorCategories.size() <= color) {
                  colorCategories.add(null);
                  colorNames.add(null);
               }

               Object category = colorCategories.get(color);
               String name = colorNames.get(color);
               if ((category == null || category.equals(categories[v])) && (name == null || names[v] == null || name.equals(names[v]))) {
                  colors[v] = color;
                  colorCategories.set(color, categories[v]);
                  if (names[v] != null) {
                     colorNames.set(color, names[v]);
                  } else {
                     names[v] = name;
                  }
                  break;
               }

               color++;
            }
         }
      }
   }

   private int[] getOrdering(Graph graph) {
      boolean[] visited = new boolean[graph.size()];
      int[] ordering = new int[graph.size()];
      int index = 0;
      int[] queue = new int[graph.size() * 2];

      for (int root = 0; root < graph.size(); root++) {
         if (!visited[root]) {
            int head = 0;
            int tail = 0;
            queue[head++] = root;

            while (head != tail) {
               int v = queue[tail++];
               if (tail == queue.length) {
                  tail = 0;
               }

               if (!visited[v]) {
                  visited[v] = true;
                  ordering[index++] = v;

                  for (int succ : graph.outgoingEdges(v)) {
                     if (!visited[succ]) {
                        if (++head == queue.length) {
                           head = 0;
                        }

                        queue[head] = succ;
                     }
                  }
               }
            }
         }
      }

      return ordering;
   }
}
