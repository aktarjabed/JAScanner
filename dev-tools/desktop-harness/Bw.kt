import aktarjabed.com.jaascanner.core.Binarize
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
fun main(args: Array<String>) {
    val img = ImageIO.read(File(args[0])); val w = img.width; val h = img.height
    val lum = IntArray(w * h) { i -> img.raster.getSample(i % w, i / w, 0) }
    val t = System.nanoTime()
    val black = Binarize.adaptive(lum, w, h)
    println("binarize ${w}x$h in ${(System.nanoTime() - t) / 1_000_000} ms")
    val out = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
    for (i in black.indices) out.raster.setSample(i % w, i / w, 0, if (black[i]) 0 else 255)
    ImageIO.write(out, "png", File(args[1]))
    // global threshold comparison
    val g = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
    for (i in lum.indices) g.raster.setSample(i % w, i / w, 0, if (lum[i] < 128) 0 else 255)
    ImageIO.write(g, "png", File(args[1].replace(".png", "_global.png")))
}
