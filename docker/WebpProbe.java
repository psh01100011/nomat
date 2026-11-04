import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

// Runs against the dependencies extracted from the final image, on its target CPU.
public class WebpProbe {
    public static void main(String[] args) throws Exception {
        var source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.drawImage(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), 0, 0, 4, 4, null);
        graphics.dispose();
        var encoded = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "webp", encoded) || encoded.size() == 0) {
            throw new IllegalStateException("WebP 인코딩 실패");
        }
        var decoded = ImageIO.read(new ByteArrayInputStream(encoded.toByteArray()));
        if (decoded == null || decoded.getWidth() != 4 || decoded.getHeight() != 4) {
            throw new IllegalStateException("WebP 디코딩 실패");
        }
        System.out.println("webp-roundtrip-ok arch=" + System.getProperty("os.arch"));
    }
}
