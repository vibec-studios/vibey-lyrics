import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Vibey Lyrics logo on a 32x32 pixel grid (same style as Vibey Multiplayer: violet/blue, dark outline):
 * a music note and two caption lines, the upper one "sung" in pink. Writes assets/icon.svg and the mod icon PNG.
 *
 * java tools/Logo.java     (from the repo root)
 */
public class Logo {
    static final int N = 32;
    static final int OUTLINE = 0x1e1b4b;
    static int[][] px = new int[N][N];

    public static void main(String[] args) throws Exception {
        for (int[] r : px) java.util.Arrays.fill(r, -1);

        // note head (tilted ellipse)
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double dx = x + 0.5 - 9.5;
                double dy = y + 0.5 - 21.0;
                double rx = dx * Math.cos(-0.35) - dy * Math.sin(-0.35);
                double ry = dx * Math.sin(-0.35) + dy * Math.cos(-0.35);
                if ((rx * rx) / (4.9 * 4.9) + (ry * ry) / (3.4 * 3.4) <= 1) px[y][x] = dy < 0 ? 0xa78bfa : 0x8b5cf6;
            }
        }
        px[19][7] = 0xede9fe;
        px[19][8] = 0xede9fe;
        px[20][7] = 0xc4b5fd;
        // stem
        for (int y = 4; y <= 20; y++) {
            px[y][13] = 0x818cf8;
            px[y][14] = 0x6366f1;
        }
        // flag
        int[][] flag = {{4, 15, 17}, {5, 15, 18}, {6, 16, 19}, {7, 17, 20}, {8, 18, 21}, {9, 19, 21}, {10, 19, 21}, {11, 20, 21}, {12, 20, 21}, {13, 20, 20}};
        for (int[] f : flag) for (int x = f[1]; x <= f[2]; x++) px[f[0]][x] = f[0] < 7 ? 0xf9a8d4 : 0xf472b6;
        // caption lines: sung part pink, the rest pale violet
        for (int y = 24; y <= 25; y++) for (int x = 17; x <= 29; x++) px[y][x] = x <= 24 ? (y == 24 ? 0xf9a8d4 : 0xec4899) : (y == 24 ? 0xddd6fe : 0xc4b5fd);
        for (int y = 28; y <= 29; y++) for (int x = 8; x <= 25; x++) px[y][x] = y == 28 ? 0x93c5fd : 0x3b82f6;
        // sparkles
        int[][] spark = {{7, 25}, {6, 25}, {8, 25}, {7, 24}, {7, 26}};
        for (int[] s : spark) px[s[0]][s[1]] = s[0] == 7 && s[1] == 25 ? 0xfef3c7 : 0xfde68a;
        px[15][28] = 0xfde68a;
        px[3][5] = 0xc4b5fd;

        // dark outline around every shape
        int[][] out = new int[N][N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) out[y][x] = px[y][x];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                if (px[y][x] != -1) continue;
                boolean near = false;
                int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] k : d) {
                    int nx = x + k[0], ny = y + k[1];
                    if (nx >= 0 && ny >= 0 && nx < N && ny < N && px[ny][nx] != -1 && !isSparkle(px[ny][nx])) near = true;
                }
                if (near) out[y][x] = OUTLINE;
            }
        }
        px = out;

        // SVG (row runs of the same color)
        List<String> rects = new ArrayList<>();
        for (int y = 0; y < N; y++) {
            int x = 0;
            while (x < N) {
                int c = px[y][x];
                int start = x;
                while (x < N && px[y][x] == c) x++;
                if (c != -1) rects.add(String.format("  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"1\" fill=\"#%06x\"/>", start, y, x - start, c));
            }
        }
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 32 32\" shape-rendering=\"crispEdges\">\n"
                + "  <!-- Vibey Lyrics: a music note and two caption lines, the upper one being sung -->\n"
                + String.join("\n", rects) + "\n</svg>\n";
        Files.writeString(Path.of("assets/icon.svg"), svg);

        for (int scale : new int[]{4, 16}) {
            BufferedImage img = new BufferedImage(N * scale, N * scale, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < N * scale; y++) {
                for (int x = 0; x < N * scale; x++) {
                    int c = px[y / scale][x / scale];
                    img.setRGB(x, y, c == -1 ? 0 : 0xFF000000 | c);
                }
            }
            File f = scale == 4 ? new File("src/main/resources/assets/vibeylyrics/icon.png") : new File("assets/icon-512.png");
            f.getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
        }
        System.out.println("Logo written");
    }

    static boolean isSparkle(int c) {
        return c == 0xfde68a || c == 0xfef3c7 || c == 0xc4b5fd && false;
    }
}
