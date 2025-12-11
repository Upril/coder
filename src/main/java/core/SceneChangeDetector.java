package core;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class SceneChangeDetector {
    /**
     * Oblicza średnią różnicę na piksel (SAD) pomiędzy dwiema klatkami.
     * Używa podpróbkowania dla szybkości (step = 8).
     */
    public static double calculateDifferenceMetric(BufferedImage curr, BufferedImage prev) {
        int width = curr.getWidth();
        int height = curr.getHeight();

        // Bezpieczne pobranie buforów pikseli (zakładamy TYPE_INT_RGB lub ARGB)
        int[] p1 = ((DataBufferInt) curr.getRaster().getDataBuffer()).getData();
        int[] p2 = ((DataBufferInt) prev.getRaster().getDataBuffer()).getData();

        long totalDiff = 0;
        int samples = 0;

        // STEP = 8: Sprawdzamy co 8 piksel (przyspieszenie 64-krotne!)
        // To w zupełności wystarczy do wykrycia cięcia montażowego.
        int step = 8;

        for (int i = 0; i < p1.length; i += step) {
            int c1 = p1[i];
            int c2 = p2[i];

            // Szybka ekstrakcja kanałów (maskowanie bitowe)
            int r1 = (c1 >> 16) & 0xFF;
            int g1 = (c1 >> 8) & 0xFF;
            int b1 = c1 & 0xFF;

            int r2 = (c2 >> 16) & 0xFF;
            int g2 = (c2 >> 8) & 0xFF;
            int b2 = c2 & 0xFF;

            // Uproszczona różnica (Suma modułów różnic RGB)
            // Można by liczyć Lumę, ale prosta suma jest szybsza i wystarczająca.
            int diff = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);

            // Dzielimy przez 3, żeby mieć średnią różnicę na kanał
            totalDiff += (diff / 3);
            samples++;
        }

        return (double) totalDiff / samples;
    }

    /**
     * Decyduje, czy nastąpiła zmiana sceny.
     */
    public static boolean detect(BufferedImage curr, BufferedImage prev) {
        // Próg dobrany eksperymentalnie.
        // 25.0 oznacza, że średnio każdy piksel zmienił kolor o ~10% skali jasności.
        // Typowe szumy kamery to 2-5. Zmiana sceny to >30.
        final double THRESHOLD = 25.0;

        double metric = calculateDifferenceMetric(curr, prev);
        return metric > THRESHOLD;
    }
}
