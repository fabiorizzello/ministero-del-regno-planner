package org.example.project.feature.output.infrastructure

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer

fun renderPdfToPngFile(pdfPath: Path, pngPath: Path) {
    Loader.loadPDF(pdfPath.toFile()).use { document ->
        val renderer = PDFRenderer(document)
        val image: BufferedImage = renderer.renderImageWithDPI(0, 200f)
        ImageIO.write(image, "png", pngPath.toFile())
    }
}
