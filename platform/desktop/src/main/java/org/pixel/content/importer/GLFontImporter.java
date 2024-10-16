package org.pixel.content.importer;

import org.pixel.content.ContentImporter;
import org.pixel.content.ContentImporterInfo;
import org.pixel.content.Font;
import org.pixel.content.FontData;
import org.pixel.content.ImportContext;
import org.pixel.content.importer.settings.FontImporterSettings;
import org.pixel.content.opengl.GLFont;

import java.nio.ByteBuffer;

import static org.lwjgl.BufferUtils.createByteBuffer;

@ContentImporterInfo(type = Font.class, extension = { ".ttf", ".otf" })
public class GLFontImporter implements ContentImporter<Font> {
    @Override
    public Font process(ImportContext ctx) {
        ByteBuffer rawBuffer = createByteBuffer(ctx.getData().length);
        rawBuffer.put(ctx.getData()).flip(); // reset position to 0
        var fontData = new FontData(rawBuffer);

        if (ctx.getSettings() instanceof FontImporterSettings) {
            var settings = (FontImporterSettings) ctx.getSettings();
            return new GLFont(fontData, settings.getFontSize(), settings.getHorizontalSpacing(),
                    settings.getVerticalSpacing(), settings.getOversampling());
        }

        return new GLFont(fontData);
    }
}
