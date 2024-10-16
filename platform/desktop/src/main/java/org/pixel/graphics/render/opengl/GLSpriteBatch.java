package org.pixel.graphics.render.opengl;

import static org.lwjgl.opengl.GL11C.GL_DST_COLOR;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_ZERO;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glBlendFunc;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL20C.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glUniform1iv;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import org.lwjgl.system.MemoryUtil;
import org.pixel.commons.Color;
import org.pixel.commons.lifecycle.State;
import org.pixel.commons.logger.Logger;
import org.pixel.commons.logger.LoggerFactory;
import org.pixel.content.Font;
import org.pixel.content.FontGlyph;
import org.pixel.content.Texture;
import org.pixel.graphics.render.BlendMode;
import org.pixel.graphics.render.SpriteBatch;
import org.pixel.graphics.shader.Shader;
import org.pixel.graphics.shader.opengl.GLVertexArrayObject;
import org.pixel.graphics.shader.opengl.GLVertexBufferObject;
import org.pixel.graphics.shader.opengl.GLMultiTextureShader;
import org.pixel.graphics.shader.opengl.GLShader;
import org.pixel.math.Matrix4;
import org.pixel.math.Rectangle;
import org.pixel.math.Vector2;

public class GLSpriteBatch extends SpriteBatch {

    private static final Logger log = LoggerFactory.getLogger(GLSpriteBatch.class);

    private static final int BUFFER_UNIT_LENGTH = 256; // maximum sprites per batch
    private static final int SPRITE_UNIT_LENGTH = 54; // number of attribute information units per sprite
                                                      // (uploadBufferData * each inner put)
    private static final int ATTRIBUTE_STRIDE = 36; // attribute stride (bytes) between each vertex info

    private static final Matrix4 spriteViewMatrix = new Matrix4();
    private final Vector2 tTopLeft = new Vector2();
    private final Vector2 tTopRight = new Vector2();
    private final Vector2 tBottomRight = new Vector2();
    private final Vector2 tBottomLeft = new Vector2();
    private final Vector2 bottomLeft = new Vector2();
    private final Vector2 bottomRight = new Vector2();
    private final Vector2 topLeft = new Vector2();
    private final Vector2 topRight = new Vector2();
    private final HashMap<Integer, Integer> shaderTextureMap = new HashMap<>();
    private final GLVertexBufferObject vbo;
    private final GLVertexArrayObject vao;
    private final FloatBuffer matrixBuffer;
    private State state = State.CREATED;

    private final int shaderTextureCount;

    private FloatBuffer dataBuffer;
    private SpriteData[] spriteData;
    private GLShader shader;
    private int bufferMaxSize;
    private int bufferWriteIndex;
    private int lastTextureId;
    private int lastDepthLevel;
    private boolean hasDifferentDepthLevels;

    /**
     * Constructor.
     */
    public GLSpriteBatch() {
        this(BUFFER_UNIT_LENGTH);
    }

    /**
     * Constructor.
     *
     * @param bufferMaxSize The maximum number of sprites that can be drawn in a
     *                      single batch.
     */
    public GLSpriteBatch(int bufferMaxSize) {
        this(bufferMaxSize, 0);
    }

    /**
     * Constructor.
     *
     * @param bufferMaxSize      The maximum number of sprites that can be drawn in
     *                           a single batch.
     * @param shaderTextureCount The number of textures to be used by the shader (if
     *                           the parameter is set to '0', the
     *                           value will be set based on the device maximum
     *                           capacity).
     */
    public GLSpriteBatch(int bufferMaxSize, int shaderTextureCount) {
        if (bufferMaxSize <= 0) {
            throw new RuntimeException("Invalid buffer size, must be greater than zero");
        }

        this.bufferMaxSize = bufferMaxSize;
        this.matrixBuffer = MemoryUtil.memAllocFloat(4 * 4);
        this.vbo = new GLVertexBufferObject();
        this.vao = new GLVertexArrayObject();
        this.bufferWriteIndex = 0;

        if (shaderTextureCount <= 0) {
            log.trace("Setting max number of textures based on 'GL_MAX_TEXTURE_IMAGE_UNITS'.");

            // get the maximum number of textures allowed by the graphics device:
            int[] textureUnits = new int[1];
            glGetIntegerv(GL_MAX_TEXTURE_IMAGE_UNITS, textureUnits);
            this.shaderTextureCount = textureUnits[0] > 0 ? textureUnits[0] : 1;

        } else {
            this.shaderTextureCount = shaderTextureCount;
        }

        log.trace("Buffer max size (units): '{}'.", this.bufferMaxSize);
        log.trace("Shader texture count: '{}'.", this.shaderTextureCount);
    }

    @Override
    public boolean init() {
        if (state.hasInitialized()) {
            log.warn("SpriteBatch already initialized.");
            return false;
        }
        state = State.INITIALIZING;

        shader = new GLMultiTextureShader(shaderTextureCount);
        shader.use();

        // since the base texture is always TEXTURE0, this can be done only once (for
        // the 'shaderTextureCount' amount)
        int[] textureRefArray = new int[shaderTextureCount];
        for (int i = 0; i < shaderTextureCount; i++) {
            textureRefArray[i] = i;
        }
        glUniform1iv(shader.getUniformLocation("uTextureImage"), textureRefArray);

        // setup attributes:
        int aVertexPosition = this.shader.getAttributeLocation("aVertexPosition");
        int aTextureCoordinates = this.shader.getAttributeLocation("aTextureCoordinates");
        int aVertexColor = this.shader.getAttributeLocation("aVertexColor");
        int aTextureId = this.shader.getAttributeLocation("aTextureIndex");

        vao.bind();
        vbo.bind(GL_ARRAY_BUFFER);

        glEnableVertexAttribArray(aVertexPosition);
        glVertexAttribPointer(aVertexPosition, 2, GL_FLOAT, false, ATTRIBUTE_STRIDE, 0);

        glEnableVertexAttribArray(aTextureCoordinates);
        glVertexAttribPointer(aTextureCoordinates, 2, GL_FLOAT, false, ATTRIBUTE_STRIDE, 2 * Float.BYTES);

        glEnableVertexAttribArray(aVertexColor);
        glVertexAttribPointer(aVertexColor, 4, GL_FLOAT, false, ATTRIBUTE_STRIDE, 4 * Float.BYTES);

        glEnableVertexAttribArray(aTextureId);
        glVertexAttribPointer(aTextureId, 1, GL_FLOAT, false, ATTRIBUTE_STRIDE, 8 * Float.BYTES);

        this.initBuffer();

        state = State.INITIALIZED;
        return true;
    }

    @Override
    public void dispose() {
        shader.dispose();
        vbo.dispose();
        vao.dispose();
        state = State.DISPOSED;
    }

    @Override
    public void draw(Texture texture, Vector2 position, Rectangle source, Color color, Vector2 anchor, float scaleX,
            float scaleY, float rotation, int depth) {
        if (lastDepthLevel >= 0 && depth != lastDepthLevel) {
            hasDifferentDepthLevels = true;
        }

        SpriteData spriteData = getNextSpriteDataObject();
        spriteData.active = true;
        spriteData.textureId = texture.getId();
        spriteData.textureWidth = texture.getWidth();
        spriteData.textureHeight = texture.getHeight();
        spriteData.x = position.getX();
        spriteData.y = position.getY();
        spriteData.width = texture.getWidth()
                * (source != null ? source.getWidth() / texture.getWidth() * scaleX : scaleX);
        spriteData.height = texture.getHeight()
                * (source != null ? source.getHeight() / texture.getHeight() * scaleY : scaleY);
        spriteData.anchor = anchor;
        spriteData.color = color;
        spriteData.source = source;
        spriteData.rotation = rotation;
        spriteData.depth = depth;

        lastDepthLevel = depth;

        spriteDataAdded();
    }

    @Override
    public void draw(Texture texture, Rectangle displayArea, Rectangle source, Color color, Vector2 anchor,
            float rotation, int depth) {
        if (lastDepthLevel >= 0 && depth != lastDepthLevel) {
            hasDifferentDepthLevels = true;
        }

        SpriteData spriteData = getNextSpriteDataObject();
        spriteData.active = true;
        spriteData.textureId = texture.getId();
        spriteData.textureWidth = texture.getWidth();
        spriteData.textureHeight = texture.getHeight();
        spriteData.x = displayArea.getX();
        spriteData.y = displayArea.getY();
        spriteData.width = displayArea.getWidth();
        spriteData.height = displayArea.getHeight();
        spriteData.anchor = anchor;
        spriteData.source = source;
        spriteData.color = color;
        spriteData.rotation = rotation;
        spriteData.depth = depth;

        lastDepthLevel = depth;

        spriteDataAdded();
    }

    @Override
    public void drawText(Font font, String text, Vector2 position, Color color, int fontSize) {
        // we are going to create a sprite data for each text character:
        float computedScale = fontSize / (float) font.getComputedFontSize();
        float scale = fontSize / (float) font.getFontSize();
        int x = (int) position.getX(); // initial x position
        int y = (int) (position.getY() + font.getFontSize() * scale + font.getVerticalSpacing());
        for (char ch : text.toCharArray()) {
            FontGlyph glyph = font.getGlyph(ch);
            if (glyph == null) {
                continue; // cannot process this char data...
            }

            if (ch == '\n') {
                y += (int) (font.getFontSize() * scale + font.getVerticalSpacing());
                x = (int) position.getX();
                continue;
            }

            var spriteData = getNextSpriteDataObject();
            spriteData.active = true;
            spriteData.textureId = font.getTextureId();
            spriteData.textureWidth = font.getTextureWidth();
            spriteData.textureHeight = font.getTextureHeight();
            spriteData.x = x + glyph.getXOffset() * scale;
            spriteData.y = y + glyph.getYOffset() * scale;
            spriteData.width = glyph.getWidth() * computedScale;
            spriteData.height = glyph.getHeight() * computedScale;
            spriteData.source = new Rectangle(glyph.getX(), glyph.getY(), glyph.getWidth(), glyph.getHeight());
            spriteData.anchor = Vector2.zero();
            spriteData.color = color;
            spriteData.rotation = 0f;

            x += (int) (glyph.getXAdvance() * scale + font.getHorizontalSpacing());

            spriteDataAdded();
        }
    }

    @Override
    public Shader getShader() {
        return shader;
    }

    @Override
    public void begin(Matrix4 viewMatrix) {
        this.begin(viewMatrix, BlendMode.NORMAL_BLEND);
    }

    @Override
    public void begin(Matrix4 viewMatrix, BlendMode blendMode) {
        dataBuffer.clear();
        shaderTextureMap.clear();
        bufferWriteIndex = 0;
        lastTextureId = -1;
        lastDepthLevel = -1;
        hasDifferentDepthLevels = false;

        if (blendMode == BlendMode.ADDITIVE) {
            glBlendFunc(GL_ONE, GL_ONE);
        } else if (blendMode == BlendMode.MULTIPLY) {
            glBlendFunc(GL_DST_COLOR, GL_ZERO);
        } else {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }

        // use shader
        shader.use();

        // bind buffers
        vao.bind();
        vbo.bind(GL_ARRAY_BUFFER);

        // apply camera matrix
        matrixBuffer.clear();
        viewMatrix.writeBuffer(matrixBuffer);
        glUniformMatrix4fv(shader.getUniformLocation("uMatrix"), false, matrixBuffer);
    }

    @Override
    public void end() {
        flush();

        vao.unbind();

        // restore global blend func
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void resizeBuffer(int newSize) {
        if (newSize <= 0) {
            throw new RuntimeException("Invalid buffer size, must be greater than zero");
        }

        if (newSize == bufferMaxSize) {
            return; // no need to resize
        }

        this.bufferMaxSize = newSize;
        this.initBuffer();
    }

    private void initBuffer() {
        if (this.dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
        }

        this.spriteData = new SpriteData[bufferMaxSize];
        this.dataBuffer = MemoryUtil.memAllocFloat(SPRITE_UNIT_LENGTH * bufferMaxSize);
        this.bufferWriteIndex = 0; // Ensure the buffer write index is reset because the buffer size has changed

        // initialize sprite data objects
        for (int i = 0; i < bufferMaxSize; i++) {
            this.spriteData[i] = new SpriteData();
        }
    }

    private SpriteData getNextSpriteDataObject() {
        return this.spriteData[this.bufferWriteIndex++];
    }

    private void putTexture(SpriteData spriteData) {
        int offset = shaderTextureMap.size();
        glActiveTexture(GL_TEXTURE0 + offset);
        glBindTexture(GL_TEXTURE_2D, spriteData.textureId);
        shaderTextureMap.put(spriteData.textureId, offset);
    }

    private void flushBatch(int count) {
        dataBuffer.flip();
        vbo.uploadData(GL_ARRAY_BUFFER, dataBuffer, GL_STATIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, 6 * count);
    }

    private void flush() {
        if (hasDifferentDepthLevels) {
            Arrays.sort(this.spriteData, (o1, o2) -> {
                if (!o1.active || !o2.active) {
                    return Boolean.compare(o2.active, o1.active);
                }
                return o1.depth - o2.depth;
            });
        }

        // draw the sprite data..
        int count = 0;
        for (int i = 0; i < bufferWriteIndex; ++i) {
            SpriteData spriteData = this.spriteData[i];
            if (!spriteData.active) {
                continue; // skip inactive sprites
            }

            if (lastTextureId != spriteData.textureId) {
                if (!shaderTextureMap.containsKey(spriteData.textureId)) {
                    if (shaderTextureMap.size() >= shaderTextureCount) {
                        flushBatch(count);
                        count = 0;
                        dataBuffer.clear();
                        shaderTextureMap.clear();
                    }
                    putTexture(spriteData);
                }

                lastTextureId = spriteData.textureId;
            }

            processSpriteData(spriteData);
            count++;
            spriteData.active = false;
        }

        flushBatch(count);
        dataBuffer.clear();

        hasDifferentDepthLevels = false;
        lastDepthLevel = -1;
        bufferWriteIndex = 0;
    }

    private void processSpriteData(SpriteData sprite) {
        // compute the sprite visualization matrix (transform the vertices according to
        // the sprite characteristics)
        computeSpriteDataViewMatrix(sprite);

        // note that both position and source data have the following coordinate
        // orientation:
        // (this depends on how the texture is loaded into memory, TextureImporter will
        // read topLeft to bottomRight)
        // ##############
        // #(0,0)..(1,0)#
        // #....\.......#
        // #..A..\...B..#
        // #......\.....#
        // #(0,1)..(1,1)#
        // ##############

        // vertex data:
        bottomLeft.set(0, 1);
        bottomLeft.transformMatrix4(spriteViewMatrix);
        bottomRight.set(1);
        bottomRight.transformMatrix4(spriteViewMatrix);
        topLeft.set(0);
        topLeft.transformMatrix4(spriteViewMatrix);
        topRight.set(1, 0);
        topRight.transformMatrix4(spriteViewMatrix);

        // texture source:
        tTopLeft.set(0);
        tTopRight.set(1, 0);
        tBottomRight.set(1);
        tBottomLeft.set(0, 1);
        if (sprite.source != null) {
            // use has defined custom source area; the org.pixel.input is relative to the
            // real width and height of
            // the source texture, therefore we need to convert into space area
            // (x=[0-1];y=[0-1])
            tTopLeft.set((sprite.source.getX() / sprite.textureWidth),
                    (sprite.source.getY() / sprite.textureHeight));
            tTopRight.set(((sprite.source.getX() + sprite.source.getWidth()) / sprite.textureWidth),
                    (sprite.source.getY() / sprite.textureHeight));
            tBottomRight.set(((sprite.source.getX() + sprite.source.getWidth()) / sprite.textureWidth),
                    ((sprite.source.getY() + sprite.source.getHeight()) / sprite.textureHeight));
            tBottomLeft.set((sprite.source.getX() / sprite.textureWidth),
                    ((sprite.source.getY() + sprite.source.getHeight()) / sprite.textureHeight));
        }

        int textureId = shaderTextureCount == 1 ? 0 : shaderTextureMap.get(sprite.textureId);

        // put the drawing data on the buffer:
        this.uploadTriangleData(bottomLeft, bottomRight, topLeft, tBottomLeft, tBottomRight, tTopLeft, sprite.color,
                textureId);
        this.uploadTriangleData(topLeft, bottomRight, topRight, tTopLeft, tBottomRight, tTopRight, sprite.color,
                textureId);
    }

    private void uploadTriangleData(Vector2 v1, Vector2 v2, Vector2 v3, Vector2 t1, Vector2 t2, Vector2 t3, Color color,
            int textureId) {
        this.uploadBufferData(v1.getX(), v1.getY(), t1.getX(), t1.getY(), color, textureId);
        this.uploadBufferData(v2.getX(), v2.getY(), t2.getX(), t2.getY(), color, textureId);
        this.uploadBufferData(v3.getX(), v3.getY(), t3.getX(), t3.getY(), color, textureId);
    }

    private void uploadBufferData(float x, float y, float tx, float ty, Color color, int textureId) {
        this.dataBuffer.put(x);
        this.dataBuffer.put(y);
        this.dataBuffer.put(tx);
        this.dataBuffer.put(ty);
        this.dataBuffer.put(color.getRed());
        this.dataBuffer.put(color.getGreen());
        this.dataBuffer.put(color.getBlue());
        this.dataBuffer.put(color.getAlpha());
        this.dataBuffer.put(textureId);
    }

    private void spriteDataAdded() {
        if (bufferWriteIndex >= bufferMaxSize) {
            flush();
        }
    }

    private void computeSpriteDataViewMatrix(SpriteData spriteData) {
        // reset
        spriteViewMatrix.setIdentity();

        // position:
        spriteViewMatrix.translate(spriteData.x - spriteData.width * spriteData.anchor.getX(),
                spriteData.y - spriteData.height * spriteData.anchor.getY(), 0);

        // rotation:
        if (spriteData.rotation != 0) {
            spriteViewMatrix.translate(spriteData.width * spriteData.anchor.getX(),
                    spriteData.height * spriteData.anchor.getY(), 0);
            spriteViewMatrix.rotate(spriteData.rotation, 0f, 0f, 1.0f);
            spriteViewMatrix.translate(-spriteData.width * spriteData.anchor.getX(),
                    -spriteData.height * spriteData.anchor.getY(), 0);
        }

        // scale:
        spriteViewMatrix.scale(spriteData.width, spriteData.height, 0.0f);
    }

    // region private classes

    /**
     * SpriteData class
     */
    private static class SpriteData {
        boolean active;
        int textureId;
        int depth;
        float textureWidth;
        float textureHeight;
        float rotation;
        float x;
        float y;
        float width;
        float height;
        Color color;
        Vector2 anchor;
        Rectangle source; // texture source area
    }

    // endregion
}
