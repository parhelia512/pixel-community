/*
 * This software is available under Apache License
 * Copyright (c) 2020
 */

package org.pixel.graphics.shader.opengl.effect;

import lombok.Getter;
import lombok.Setter;
import org.pixel.graphics.shader.opengl.GLShader;
import org.pixel.math.Vector2;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform2f;

@Getter
@Setter
public class GLNoiseEffectShader extends GLShader {

    private static final List<String> uniforms = Arrays.asList("uTextureImage", "uAmount", "uOffset", "uDensity");
    private static final List<String> attributes = Collections.singletonList("aVertexPosition");

    private static final String vertSrc;
    private static final String fragSrc;

    static {
        vertSrc = loadShader("engine/shader/opengl/effect/noise.vert.glsl");
        fragSrc = loadShader("engine/shader/opengl/effect/noise.frag.glsl");
    }

    private float amount;
    private float density;
    private Vector2 offset;

    /**
     * Constructor.
     *
     * @param amount  The amount of noise to apply (0-1).
     * @param density The density of the noise (0-1).
     */
    public GLNoiseEffectShader(float amount, float density) {
        super(vertSrc, fragSrc, attributes, uniforms);
        this.amount = amount;
        this.density = density;
        this.offset = Vector2.zero();
        this.init();
    }

    @Override
    public void apply() {
        glUniform1f(getUniformLocation("uAmount"), amount);
        glUniform1f(getUniformLocation("uDensity"), density);
        glUniform2f(getUniformLocation("uOffset"), offset.getX(), offset.getY());
    }
}

