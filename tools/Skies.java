import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Paints the custom skies of Vibey Lyrics: the upper half of an equirectangular panorama per sky (left to right is a
 * full turn, top row is straight up, bottom row the horizon). Everything is made from noise and math here, no photos.
 * Run: java tools/Skies.java   (writes src/main/resources/assets/vibeylyrics/textures/sky/*.png and docs/skies.png)
 */
public class Skies {
    static final int W = 1536;
    static final int H = 512;
    static final String OUT = "src/main/resources/assets/vibeylyrics/textures/sky/";

    interface Sky {
        float[] color(double[] d, double elev, double lon);
    }

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        String only = args.length > 0 ? args[0] : null;
        Object[][] skies = {
                {"sunset", (Sky) Skies::sunset},
                {"golden", (Sky) Skies::golden},
                {"candy", (Sky) Skies::candy},
                {"dreamy", (Sky) Skies::dreamy},
                {"galaxy", (Sky) Skies::galaxy},
                {"nebula", (Sky) Skies::nebula},
                {"aurora", (Sky) Skies::aurora},
                {"saturn", (Sky) Skies::saturn},
                {"moonlight", (Sky) Skies::moonlight},
                {"synthwave", (Sky) Skies::synthwave},
                {"crimson", (Sky) Skies::crimson},
        };
        BufferedImage sheet = new BufferedImage(4 * 384, 3 * 128, BufferedImage.TYPE_INT_RGB);
        int n = 0;
        for (Object[] s : skies) {
            String name = (String) s[0];
            if (only != null && !only.equals(name)) { n++; continue; }
            long t0 = System.currentTimeMillis();
            BufferedImage img = render((Sky) s[1]);
            ImageIO.write(img, "png", new File(OUT + name + ".png"));
            java.awt.Graphics2D g = sheet.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, (n % 4) * 384, (n / 4) * 128, 384, 128, null);
            g.dispose();
            int[] avg = horizon(img, H - 6, H);
            int[] top = horizon(img, H / 3, H / 3 + 6);
            System.out.printf("%-10s %5d ms  %4d KB  horizon 0x%02X%02X%02X  high 0x%02X%02X%02X%n", name, System.currentTimeMillis() - t0,
                    new File(OUT + name + ".png").length() / 1024, avg[0], avg[1], avg[2], top[0], top[1], top[2]);
            n++;
        }
        if (only == null) ImageIO.write(sheet, "png", new File("build/skies-sheet.png"));
        ImageIO.write(streak(), "png", new File(OUT + "streak.png"));
    }

    /** Shooting star: bright head on the right, tail fading out to the left. */
    static BufferedImage streak() {
        int w = 128, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double u = (x + 0.5) / w, v = (y + 0.5) / h - 0.5;
            double width = 0.06 + 0.16 * u * u;
            double a = Math.pow(u, 2.2) * Math.exp(-v * v / (2 * width * width));
            a += Math.exp(-((u - 0.96) * (u - 0.96) * 900 + v * v * 60)) * 0.8;
            int al = (int) Math.round(Math.min(1, a) * 255);
            img.setRGB(x, y, (al << 24) | 0xFFFFFF);
        }
        return img;
    }

    static int[] horizon(BufferedImage img, int y0, int y1) {
        long r = 0, g = 0, b = 0, c = 0;
        for (int y = y0; y < y1; y++) for (int x = 0; x < W; x++) {
            int p = img.getRGB(x, y);
            r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; c++;
        }
        return new int[]{(int) (r / c), (int) (g / c), (int) (b / c)};
    }

    static BufferedImage render(Sky sky) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        java.util.stream.IntStream.range(0, H).parallel().forEach(y -> {
            Random rnd = new Random(y * 7919L);
            for (int x = 0; x < W; x++) {
                double lon = (x + 0.5) / W * Math.PI * 2;
                double elev = (1 - (y + 0.5) / H) * Math.PI / 2;
                double ce = Math.cos(elev);
                double[] d = {ce * Math.sin(lon), Math.sin(elev), ce * Math.cos(lon)};
                float[] c = sky.color(d, elev, lon);
                int r = px(c[0], rnd), g = px(c[1], rnd), b = px(c[2], rnd);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        });
        // The last few rows melt into the average horizon color, which is also the fog color in game, so the band
        // below the horizon (which repeats the last row) is calm and the land fades into the sky without a seam.
        int[] avg = horizon(img, H - 4, H);
        int blend = 14;
        for (int y = H - blend; y < H; y++) {
            double k = Math.pow((y - (H - blend) + 1) / (double) blend, 1.5);
            for (int x = 0; x < W; x++) {
                int p = img.getRGB(x, y);
                int r = (int) Math.round(((p >> 16) & 255) * (1 - k) + avg[0] * k);
                int g = (int) Math.round(((p >> 8) & 255) * (1 - k) + avg[1] * k);
                int b = (int) Math.round((p & 255) * (1 - k) + avg[2] * k);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    static int px(double v, Random rnd) {
        double s = Math.max(0, Math.min(1, v)) * 255 + rnd.nextDouble() - 0.5;
        return (int) Math.max(0, Math.min(255, Math.round(s)));
    }

    // ===================================================================================================== skies

    static float[] sunset(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.12, 0.35, 1}, hex(0xFFB27A), hex(0xF59A5C), hex(0xE07F48), hex(0xB8613E));
        double[] sun = dir(Math.toRadians(250), Math.toRadians(4));
        double sd = dot(d, sun);
        c = add(c, hex(0xFFE2A8), 0.55 * Math.pow(Math.max(0, sd), 12));
        c = add(c, hex(0xFFF4D6), 0.6 * Math.pow(Math.max(0, sd), 300));
        // long streaky clouds, lit from the sun side
        double n = fbm(d[0] * 2.2, d[1] * 9, d[2] * 2.2, 6, 11);
        double cl = smooth(0.48, 0.72, n) * (1 - smooth(0.55, 0.95, t));
        double lit = 0.5 + 0.5 * sd;
        float[] cloud = mix(hex(0xC8664A), hex(0xFFD2A6), lit);
        c = mix(c, cloud, cl * 0.8);
        double hi = smooth(0.62, 0.8, n) * (1 - t);
        return add(c, hex(0xFFE6C4), hi * 0.25 * lit);
    }

    static float[] golden(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.1, 0.3, 0.6, 1}, hex(0xFFD98A), hex(0xFBC17A), hex(0xE9B1A0), hex(0x9FA8E0), hex(0x6F84D8));
        double[] sun = dir(Math.toRadians(80), Math.toRadians(7));
        double sd = dot(d, sun);
        c = add(c, hex(0xFFF0B8), 0.7 * Math.pow(Math.max(0, sd), 20));
        c = add(c, hex(0xFFFFF0), 1.0 * smooth(0.9993, 0.9996, sd));
        double n = fbm(d[0] * 3, d[1] * 7, d[2] * 3, 6, 21);
        double cl = smooth(0.52, 0.75, n) * (1 - smooth(0.4, 0.9, t));
        return mix(c, mix(hex(0xF0A878), hex(0xFFF1D0), 0.5 + 0.5 * sd), cl * 0.75);
    }

    static float[] candy(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.2, 0.55, 1}, hex(0xFFC9E3), hex(0xF7B0E0), hex(0xC4A6F5), hex(0x9C8CF2));
        // puffy pastel clouds
        double n = fbm(d[0] * 3.2 + 4, d[1] * 4.5, d[2] * 3.2, 6, 33);
        double cl = smooth(0.5, 0.66, n);
        double shade = fbm(d[0] * 3.2 + 4.05, d[1] * 4.5 - 0.06, d[2] * 3.2, 5, 33);
        float[] cloud = mix(hex(0xD9A2E8), hex(0xFFF2FA), smooth(0.45, 0.75, shade));
        c = mix(c, cloud, cl * 0.9);
        return add(c, hex(0xFFFFFF), stars(d, 300, 0.02, 3) * smooth(0.7, 1, t) * 0.5);
    }

    static float[] dreamy(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.12, 0.35, 0.7, 1}, hex(0xF9B9C8), hex(0xD7A1D6), hex(0x8E86D8), hex(0x3E4FA8), hex(0x24307A));
        double n = fbm(d[0] * 1.6, d[1] * 10, d[2] * 1.6, 5, 44);
        c = mix(c, hex(0xFFD6E6), smooth(0.55, 0.8, n) * (1 - t) * 0.6);
        return add(c, hex(0xFFFFFF), stars(d, 260, 0.05, 4) * smooth(0.45, 0.9, t));
    }

    static float[] galaxy(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.15, 0.5, 1}, hex(0x241C4A), hex(0x141236), hex(0x090A1E), hex(0x05060F));
        double[] nrm = norm(new double[]{0.35, 0.55, -0.76});
        double p = dot(d, nrm);
        double band = Math.exp(-p * p / (2 * 0.16 * 0.16));
        double core = Math.exp(-p * p / (2 * 0.06 * 0.06));
        double n = fbm(d[0] * 5, d[1] * 5, d[2] * 5, 7, 55);
        double dust = smooth(0.5, 0.72, fbm(d[0] * 6 + 3, d[1] * 6, d[2] * 6, 5, 56)) * core;
        float[] glow = mix(hex(0x6A5ED8), hex(0xFFE9D2), core);
        c = add(c, glow, band * (0.3 + 0.7 * n) * 0.8);
        c = add(c, hex(0xD070E0), band * smooth(0.55, 0.8, n) * 0.3);
        c = add(c, hex(0x40A0FF), band * smooth(0.6, 0.85, fbm(d[0] * 4, d[1] * 4 + 9, d[2] * 4, 5, 57)) * 0.25);
        c = mul(c, 1 - 0.4 * dust);
        double s = stars(d, 420, 0.09, 1) + stars(d, 900, 0.18 + 0.3 * band, 2) * 0.7;
        return add(c, starTint(d), s);
    }

    static float[] nebula(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.2, 0.6, 1}, hex(0x6A2C9C), hex(0x461F7A), hex(0x26104F), hex(0x140832));
        // domain-warped clouds
        double wx = fbm(d[0] * 2 + 1, d[1] * 2, d[2] * 2, 4, 61) - 0.5;
        double wy = fbm(d[0] * 2, d[1] * 2 + 7, d[2] * 2, 4, 62) - 0.5;
        double n = fbm(d[0] * 2.5 + wx * 2.2, d[1] * 2.5 + wy * 2.2, d[2] * 2.5, 7, 63);
        double m = fbm(d[0] * 3 + wy * 3, d[1] * 3, d[2] * 3 + wx * 3, 6, 64);
        float[] neb = mix(hex(0x7B3BFF), hex(0xFF4FD8), smooth(0.3, 0.7, m));
        c = add(c, neb, smooth(0.42, 0.8, n) * 0.75);
        c = add(c, hex(0xFFD0F4), smooth(0.68, 0.9, n) * 0.35);
        c = add(c, hex(0x4A7BFF), smooth(0.55, 0.85, m) * smooth(0.4, 0.6, n) * 0.3);
        double s = stars(d, 520, 0.12, 5) + stars(d, 1100, 0.2, 6) * 0.6;
        return add(c, starTint(d), s * (1 - 0.5 * smooth(0.5, 0.8, n)));
    }

    static float[] aurora(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.15, 0.5, 1}, hex(0x0E3A4C), hex(0x0A2A3C), hex(0x061826), hex(0x030C16));
        double total = 0;
        float[] acc = {0, 0, 0};
        for (int k = 0; k < 3; k++) {
            // a curtain: a wavy line around the sky, rays hanging down from it
            double base = Math.toRadians(14 + k * 9) + 0.12 * Math.sin(lon * 2 + k * 1.7) + 0.05 * Math.sin(lon * 7 + k);
            double h = (e - base) / Math.toRadians(16 + k * 4);
            if (h < -0.15 || h > 1.2) continue;
            double rays = 0.55 + 0.45 * fbm(Math.cos(lon) * 30 + k * 10, Math.sin(lon) * 30, k * 3.1, 3, 70 + k);
            double prof = smooth(-0.15, 0.05, h) * Math.exp(-Math.max(0, h) * 2.6);
            double strength = prof * rays * (0.55 + 0.45 * Math.sin(lon * 3 + k * 2.1));
            float[] col = mix(hex(0x3CFFA0), hex(0xB04CFF), smooth(0.15, 0.9, h + k * 0.15));
            acc = add(acc, col, strength);
            total += strength;
        }
        c = add(c, acc, 0.9);
        c = add(c, hex(0x2AFFB0), Math.exp(-e * 6) * 0.12);
        return add(c, starTint(d), (stars(d, 480, 0.1, 7) + stars(d, 1000, 0.18, 8) * 0.6) * (1 - Math.min(1, total)));
    }

    static float[] saturn(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.2, 1}, hex(0x1A1A3A), hex(0x0D0D24), hex(0x05050F));
        c = add(c, hex(0x3A2A6A), 0.25 * smooth(0.5, 0.9, fbm(d[0] * 2.5, d[1] * 2.5, d[2] * 2.5, 5, 81)));
        c = add(c, starTint(d), stars(d, 500, 0.1, 9) + stars(d, 1000, 0.15, 10) * 0.6);
        double[] ctr = dir(Math.toRadians(40), Math.toRadians(32));
        double r = 0.2;
        double[] lightDir = norm(new double[]{-0.8, 0.5, -0.3});
        // ring plane seen at a shallow angle: "up" on screen, leaned 18 degrees towards us and a bit sideways
        double[] up = {0, 1, 0};
        double[] side = norm(cross(ctr, up));
        double[] scrUp = norm(cross(side, ctr));
        double lean = Math.toRadians(18), roll = Math.toRadians(-14);
        double[] tilt = norm(new double[]{scrUp[0] * Math.cos(roll) + side[0] * Math.sin(roll), scrUp[1] * Math.cos(roll) + side[1] * Math.sin(roll), scrUp[2] * Math.cos(roll) + side[2] * Math.sin(roll)});
        double[] ringN = norm(new double[]{tilt[0] * Math.cos(lean) - ctr[0] * Math.sin(lean), tilt[1] * Math.cos(lean) - ctr[1] * Math.sin(lean), tilt[2] * Math.cos(lean) - ctr[2] * Math.sin(lean)});
        // sphere hit
        double b = dot(d, ctr);
        double disc = b * b - (1 - r * r);
        double tS = disc >= 0 ? b - Math.sqrt(disc) : Double.MAX_VALUE;
        // ring plane hit
        double denom = dot(d, ringN);
        double tR = Math.abs(denom) > 1e-6 ? dot(ctr, ringN) / denom : -1;
        float[] ringCol = null;
        double ringA = 0;
        if (tR > 0) {
            double[] p = {d[0] * tR - ctr[0], d[1] * tR - ctr[1], d[2] * tR - ctr[2]};
            double rr = Math.sqrt(dot(p, p)) / r;
            if (rr > 1.35 && rr < 2.35) {
                double bands = 0.55 + 0.45 * Math.sin(rr * 38) * Math.sin(rr * 11 + 1);
                double gap = smooth(0.02, 0.05, Math.abs(rr - 1.95));
                ringA = bands * gap * smooth(1.35, 1.45, rr) * (1 - smooth(2.25, 2.35, rr)) * 0.85;
                // the planet's shadow on the ring
                double[] pw = {d[0] * tR, d[1] * tR, d[2] * tR};
                double[] toC = {ctr[0] - pw[0], ctr[1] - pw[1], ctr[2] - pw[2]};
                double along = dot(toC, lightDir);
                double perp2 = dot(toC, toC) - along * along;
                double shadow = along > 0 && perp2 < r * r ? 0.25 : 1;
                ringCol = mul(mix(hex(0xC9B08A), hex(0xF2E3C4), bands), shadow);
            }
        }
        if (ringCol != null && tR > tS) c = mix(c, ringCol, ringA); // ring behind the planet: drawn first
        if (tS != Double.MAX_VALUE) {
            double[] hit = {d[0] * tS, d[1] * tS, d[2] * tS};
            double[] nn = norm(new double[]{hit[0] - ctr[0], hit[1] - ctr[1], hit[2] - ctr[2]});
            double lat = dot(nn, ringN);
            double stripes = fbm(lat * 14, lat * 3, 0.5, 4, 83);
            float[] body = mix(hex(0xB98A5A), hex(0xF0D5A0), smooth(0.3, 0.7, stripes));
            body = mix(body, hex(0xE8C090), 0.3 * Math.sin(lat * 25) * Math.sin(lat * 25));
            double light = Math.max(0, dot(nn, lightDir));
            double rim = Math.pow(1 - Math.max(0, -dot(nn, d)), 3);
            body = mul(body, 0.08 + 0.95 * light);
            body = add(body, hex(0xFFD8A8), rim * 0.15 * light);
            c = mix(c, body, smooth(0, 0.003, disc));
        }
        if (ringCol != null && tR <= tS) c = mix(c, ringCol, ringA);
        // a small moon
        double md = dot(d, dir(Math.toRadians(95), Math.toRadians(52)));
        c = mix(c, hex(0xCFC8E0), smooth(0.99993, 0.99996, md));
        return c;
    }

    static float[] moonlight(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.12, 0.45, 1}, hex(0x34508F), hex(0x223A78), hex(0x13235A), hex(0x0A1440));
        double[] moon = dir(Math.toRadians(140), Math.toRadians(28));
        double md = dot(d, moon);
        c = add(c, hex(0x8FA8FF), 0.4 * Math.pow(Math.max(0, md), 60));
        c = add(c, hex(0xCFD8FF), 0.25 * Math.pow(Math.max(0, md), 900));
        double n = fbm(d[0] * 1.8, d[1] * 8, d[2] * 1.8, 5, 91);
        double cl = smooth(0.55, 0.78, n) * (1 - smooth(0.4, 0.8, t));
        c = mix(c, mix(hex(0x2A3A70), hex(0x9DB0F0), Math.pow(Math.max(0, md), 6)), cl * 0.7);
        c = add(c, starTint(d), (stars(d, 460, 0.1, 11) + stars(d, 1000, 0.16, 12) * 0.6) * (1 - cl));
        double r = 0.065;
        double ang = Math.acos(Math.min(1, md));
        if (ang < r * 1.05) {
            double k = ang / r;
            double crater = fbm(d[0] * 60, d[1] * 60, d[2] * 60, 4, 93);
            float[] surf = mix(hex(0xB8BED6), hex(0xF4F6FF), smooth(0.35, 0.7, crater));
            surf = mul(surf, 1 - 0.25 * k * k);
            c = mix(c, surf, 1 - smooth(0.96, 1.0, k));
        }
        return c;
    }

    static float[] synthwave(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.08, 0.25, 0.55, 1}, hex(0xFF5FA2), hex(0xD9408F), hex(0x7A2190), hex(0x2E0F5E), hex(0x12062E));
        // the striped retro sun, half sunk below the horizon
        double[] sun = dir(Math.toRadians(0), Math.toRadians(0));
        double ang = Math.acos(Math.min(1, dot(d, sun)));
        double r = Math.toRadians(17);
        if (ang < r) {
            double h = e / r; // 0 at horizon, 1 at the top of the sun
            float[] col = mix(hex(0xFF3D8B), hex(0xFFE36E), smooth(0, 0.9, h));
            double stripe = h < 0.55 ? smooth(0.5, 0.56, Math.abs(((h * 9) % 1) - 0.5) + h * 0.55) : 1;
            c = mix(c, col, stripe * (1 - smooth(0.97, 1, ang / r)));
        }
        c = add(c, hex(0xFF5FA2), 0.35 * Math.exp(-ang * 2.2));
        return add(c, hex(0xE8D8FF), (stars(d, 380, 0.08, 13) + stars(d, 900, 0.1, 14) * 0.5) * smooth(0.2, 0.6, t));
    }

    static float[] crimson(double[] d, double e, double lon) {
        double t = e / (Math.PI / 2);
        float[] c = grad(t, new double[]{0, 0.15, 0.5, 1}, hex(0x7A1424), hex(0x4E0C1C), hex(0x260611), hex(0x120208));
        double[] moon = dir(Math.toRadians(200), Math.toRadians(30));
        double md = dot(d, moon);
        c = add(c, hex(0xFF3B3B), 0.45 * Math.pow(Math.max(0, md), 40));
        double n = fbm(d[0] * 2, d[1] * 7, d[2] * 2, 5, 101);
        c = mix(c, hex(0x2A0509), smooth(0.5, 0.75, n) * (1 - t) * 0.6);
        c = add(c, hex(0xFFD0D0), (stars(d, 420, 0.08, 15)) * 0.8);
        double r = 0.085;
        double ang = Math.acos(Math.min(1, md));
        if (ang < r * 1.05) {
            double k = ang / r;
            double crater = fbm(d[0] * 50, d[1] * 50, d[2] * 50, 4, 103);
            float[] surf = mix(hex(0x9A1C1C), hex(0xFF6A4A), smooth(0.3, 0.7, crater));
            surf = mul(surf, 1 - 0.35 * k * k);
            c = mix(c, surf, 1 - smooth(0.96, 1.0, k));
        }
        return c;
    }

    // ===================================================================================================== helpers

    /** Stars: one per cell of a fine grid on the sphere, with a chance to exist; brightness falls off with angle. */
    static double stars(double[] d, double cells, double density, int seed) {
        double x = d[0] * cells, y = d[1] * cells, z = d[2] * cells;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double h = hash(ix, iy, iz, seed);
        if (h > density) return 0;
        double ox = 0.3 + 0.4 * hash(ix, iy, iz, seed + 1);
        double oy = 0.3 + 0.4 * hash(ix, iy, iz, seed + 2);
        double oz = 0.3 + 0.4 * hash(ix, iy, iz, seed + 3);
        double dx = x - ix - ox, dy = y - iy - oy, dz = z - iz - oz;
        double dist2 = dx * dx + dy * dy + dz * dz;
        double size = 0.012 + 0.03 * Math.pow(hash(ix, iy, iz, seed + 4), 3);
        double bright = 0.35 + 0.65 * Math.pow(hash(ix, iy, iz, seed + 5), 2);
        return bright * Math.exp(-dist2 / size) ;
    }

    static float[] starTint(double[] d) {
        double h = hash((int) (d[0] * 900), (int) (d[1] * 900), (int) (d[2] * 900), 99);
        return h < 0.2 ? hex(0xBFD4FF) : h < 0.3 ? hex(0xFFE2B8) : hex(0xFFFFFF);
    }

    static double hash(int x, int y, int z, int seed) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L + seed * 144665L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return ((h & 0xFFFFFF) / (double) 0x1000000);
    }

    // improved Perlin noise
    static final int[] P = new int[512];
    static {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(1337);
        for (int i = 255; i > 0; i--) { int j = r.nextInt(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
        for (int i = 0; i < 512; i++) P[i] = p[i & 255];
    }

    static double noise(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255, Z = (int) Math.floor(z) & 255;
        x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int A = P[X] + Y, AA = P[A] + Z, AB = P[A + 1] + Z, B = P[X + 1] + Y, BA = P[B] + Z, BB = P[B + 1] + Z;
        return lerp(w, lerp(v, lerp(u, grad(P[AA], x, y, z), grad(P[BA], x - 1, y, z)),
                        lerp(u, grad(P[AB], x, y - 1, z), grad(P[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(P[AA + 1], x, y, z - 1), grad(P[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(P[AB + 1], x, y - 1, z - 1), grad(P[BB + 1], x - 1, y - 1, z - 1))));
    }

    static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    static double lerp(double t, double a, double b) { return a + t * (b - a); }
    static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y, v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** Fractal noise in [0, 1]. */
    static double fbm(double x, double y, double z, int oct, int seed) {
        double sum = 0, amp = 0.5, f = 1, norm = 0;
        double off = seed * 17.31;
        for (int i = 0; i < oct; i++) {
            sum += amp * noise(x * f + off, y * f + off * 0.7, z * f - off * 0.3);
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return Math.max(0, Math.min(1, 0.5 + sum / norm * 0.9));
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static float[] hex(int rgb) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
    }

    static float[] mix(float[] a, float[] b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new float[]{(float) (a[0] + (b[0] - a[0]) * t), (float) (a[1] + (b[1] - a[1]) * t), (float) (a[2] + (b[2] - a[2]) * t)};
    }

    static float[] add(float[] a, float[] b, double k) {
        return new float[]{(float) (a[0] + b[0] * k), (float) (a[1] + b[1] * k), (float) (a[2] + b[2] * k)};
    }

    static float[] mul(float[] a, double k) {
        return new float[]{(float) (a[0] * k), (float) (a[1] * k), (float) (a[2] * k)};
    }

    static float[] grad(double t, double[] stops, float[]... cols) {
        for (int i = 0; i < stops.length - 1; i++) {
            if (t <= stops[i + 1]) return mix(cols[i], cols[i + 1], (t - stops[i]) / (stops[i + 1] - stops[i]));
        }
        return cols[cols.length - 1];
    }

    static double[] dir(double lon, double elev) {
        double ce = Math.cos(elev);
        return new double[]{ce * Math.sin(lon), Math.sin(elev), ce * Math.cos(lon)};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }
}
