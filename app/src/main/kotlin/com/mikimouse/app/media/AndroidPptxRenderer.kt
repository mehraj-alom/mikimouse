package com.mikimouse.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Real PPTX generation for Android — no java.awt dependency.
 * Builds a valid OOXML PPTX (zipped XML) directly on device.
 */
object AndroidPptxRenderer {
    data class Slide(val title: String, val bullets: List<String>)

    fun render(slides: List<Slide>, out: File): File {
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()

        ZipOutputStream(out.outputStream().buffered()).use { z ->
            // [Content_Types].xml
            z.putNextEntry(ZipEntry("[Content_Types].xml"))
            z.write(contentTypesXml().toByteArray())
            z.closeEntry()

            // _rels/.rels
            z.putNextEntry(ZipEntry("_rels/.rels"))
            z.write(relsXml().toByteArray())
            z.closeEntry()

            // ppt/_rels/.rels
            z.putNextEntry(ZipEntry("ppt/_rels/.rels"))
            z.write(pptRelsXml().toByteArray())
            z.closeEntry()

            // ppt/presentation.xml
            z.putNextEntry(ZipEntry("ppt/presentation.xml"))
            z.write(presentationXml(slides.size).toByteArray())
            z.closeEntry()

            // Each slide
            slides.forEachIndexed { index, slide ->
                z.putNextEntry(ZipEntry("ppt/slides/slide${index + 1}.xml"))
                z.write(slideXml(slide, index + 1).toByteArray())
                z.closeEntry()
            }

            // ppt/theme/theme1.xml
            z.putNextEntry(ZipEntry("ppt/theme/theme1.xml"))
            z.write(themeXml().toByteArray())
            z.closeEntry()

            // ppt/slides/_rels/slide1.xml.rels (for each slide)
            slides.forEachIndexed { index, _ ->
                z.putNextEntry(ZipEntry("ppt/slides/_rels/slide${index + 1}.xml.rels"))
                z.write(slideRelsXml().toByteArray())
                z.closeEntry()
            }
        }
        return out
    }

    private fun contentTypesXml() = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        appendLine("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        appendLine("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        appendLine("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        appendLine("  <Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
        appendLine("  <Override PartName=\"/ppt/slides/slide1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
        appendLine("  <Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
        appendLine("</Types>")
    }

    private fun relsXml() = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        appendLine("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        appendLine("  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/presentation\" Target=\"ppt/presentation.xml\"/>")
        appendLine("</Relationships>")
    }

    private fun pptRelsXml() = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        appendLine("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        appendLine("  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>")
        for (i in 1..slidesSize) {
            appendLine("  <Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${i}.xml\"/>")
        }
        appendLine("</Relationships>")
    }

    private var slidesSize = 0
    private fun presentationXml(slideCount: Int): String {
        slidesSize = slideCount
        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            appendLine("<p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">")
            appendLine("  <p:sldSz cx=\"12192000\" cy=\"6858000\"/>")
            appendLine("  <p:sldIdLst>")
            for (i in 1..slideCount) {
                appendLine("    <p:sldId id=\"${i + 255}\" r:id=\"rId${i + 1}\"/>")
            }
            appendLine("  </p:sldIdLst>")
            appendLine("  <p:notesSz cx=\"7620000\" cy=\"5715000\"/>")
            appendLine("</p:presentation>")
        }
    }

    private fun slideXml(slide: Slide, index: Int) = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        appendLine("<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">")
        appendLine("  <p:cSld>")
        appendLine("    <p:spTree>")
        appendLine("      <p:nvGrpSpPr>")
        appendLine("        <p:cnvPr id=\"1\" name=\"\"/>")
        appendLine("        <p:grpSpPr/>")
        appendLine("      </p:nvGrpSpPr>")
        appendLine("      <p:nvGrpSpPr>")
        appendLine("        <p:cnvPr id=\"2\" name=\"\"/>")
        appendLine("        <p:grpSpPr/>")
        appendLine("      </p:nvGrpSpPr>")
        // Title
        appendLine("      <p:sp>")
        appendLine("        <p:nvSpPr>")
        appendLine("          <p:cnvPr id=\"3\" name=\"Title ${index}\"/>")
        appendLine("          <p:ph type=\"title\"/>")
        appendLine("        </p:nvSpPr>")
        appendLine("        <p:spPr/>")
        appendLine("        <p:txBody>")
        appendLine("          <a:bodyPr/>")
        appendLine("          <a:lstStyle/>")
        appendLine("          <a:p>")
        appendLine("            <a:r>")
        appendLine("              <a:rPr lang=\"en-US\" sz=\"2400\" b=\"1\"/>")
        appendLine("              <a:t>${escapeXml(slide.title)}</a:t>")
        appendLine("            </a:r>")
        appendLine("          </a:p>")
        appendLine("        </p:txBody>")
        appendLine("      </p:sp>")
        // Bullets
        var bulletId = 4
        slide.bullets.forEach { bullet ->
            appendLine("      <p:sp>")
            appendLine("        <p:nvSpPr>")
            appendLine("          <p:cnvPr id=\"$bulletId\" name=\"Bullet ${index}-${bulletId}\"/>")
            appendLine("          <p:ph type=\"body\"/>")
            appendLine("        </p:nvSpPr>")
            appendLine("        <p:spPr/>")
            appendLine("        <p:txBody>")
            appendLine("          <a:bodyPr/>")
            appendLine("          <a:lstStyle/>")
            appendLine("          <a:p>")
            appendLine("            <a:pPr marL=\"228600\" indent=\"-228600\">")
            appendLine("              <a:buClr>")
            appendLine("                <a:srgbClr val=\"FF444444\"/>")
            appendLine("              </a:buClr>")
            appendLine("              <a:buSzPct val=\"90000\"/>")
            appendLine("              <a:buAutoNum type=\"alphaPeriod\"/>")
            appendLine("            </a:pPr>")
            appendLine("            <a:r>")
            appendLine("              <a:rPr lang=\"en-US\" sz=\"1400\"/>")
            appendLine("              <a:t>${escapeXml(bullet)}</a:t>")
            appendLine("            </a:r>")
            appendLine("          </a:p>")
            appendLine("        </p:txBody>")
        appendLine("      </p:sp>")
            bulletId++
        }
        appendLine("      <p:spPr/>")
        appendLine("    </p:spTree>")
        appendLine("    <p:clrMapOvr>")
        appendLine("      <a:masterClrMapping/>")
        appendLine("    </p:clrMapOvr>")
        appendLine("  </p:cSld>")
        appendLine("  <p:clrMap>")
        appendLine("    <a:overrideClrMapping/>")
        appendLine("  </p:clrMap>")
        appendLine("</p:sld>")
    }

    private fun slideRelsXml() = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        appendLine("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>")
    }

    private fun themeXml() = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        appendLine("<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office Theme\">")
        appendLine("  <a:slideSize cx=\"12192000\" cy=\"6858000\" type=\"screen4:3\"/>")
        appendLine("  <a:clrScheme name=\"Office\">")
        appendLine("    <a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>")
        appendLine("    <a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>")
        appendLine("    <a:dk2><a:srgbClr val=\"444444\"/></a:dk2>")
        appendLine("    <a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>")
        appendLine("    <a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1>")
        appendLine("    <a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2>")
        appendLine("    <a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>")
        appendLine("    <a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>")
        appendLine("    <a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5>")
        appendLine("    <a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6>")
        appendLine("    <a:hlink><a:srgbClr val=\"0000FF\"/></a:hlink>")
        appendLine("    <a:folHlink><a:srgbClr val=\"808080\"/></a:folHlink>")
        appendLine("  </a:clrScheme>")
        appendLine("  <a:fontScheme name=\"Office\">")
        appendLine("    <a:majorFont><a:latin typeface=\"Calibri\"/></a:majorFont>")
        appendLine("    <a:minorFont><a:latin typeface=\"Calibri\"/></a:minorFont>")
        appendLine("  </a:fontScheme>")
        appendLine("  <a:bgStyle/>")
        appendLine("  <a:extLst>")
        appendLine("    <a:ext uri=\"{05C60545-5E83-4FD7-8E4F-95C78C6F4187}\">")
        appendLine("      <a14:theme14 xmlns:a14=\"http://schemas.microsoft.com/office/drawing/2010/main\"/>")
        appendLine("    </a:ext>")
        appendLine("  </a:extLst>")
        appendLine("</a:theme>")
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
