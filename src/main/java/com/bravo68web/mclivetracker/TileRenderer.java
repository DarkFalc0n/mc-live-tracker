package com.bravo68web.mclivetracker;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class TileRenderer {
    public static byte[] renderPlaceholderPng(int z, int x, int y) throws IOException {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // background
            g.setColor(new Color(30, 30, 36));
            g.fillRect(0, 0, size, size);
            // grid
            g.setColor(new Color(70, 70, 80));
            for (int i = 0; i <= size; i += 32) {
                g.drawLine(i, 0, i, size);
                g.drawLine(0, i, size, i);
            }
            // crosshair
            g.setColor(new Color(120, 180, 255));
            g.drawLine(size/2, 0, size/2, size);
            g.drawLine(0, size/2, size, size/2);
            // text
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            String label = String.format("z=%d x=%d y=%d", z, x, y);
            g.drawString(label, 10, size - 12);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
