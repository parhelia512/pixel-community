/*
 * This software is available under Apache License
 * Copyright (c) 2020
 */

package org.pixel.gui.component;

import org.pixel.commons.DeltaTime;
import org.pixel.commons.attribute.Solidity;
import org.pixel.graphics.Color;
import org.pixel.graphics.render.RenderEngine2D;
import org.pixel.gui.common.UIContext;
import org.pixel.gui.style.StyleUtils;
import org.pixel.gui.style.model.ShadowType;
import org.pixel.gui.style.properties.*;

public abstract class UIBoxComponent extends UIComponent {

    protected PaddingStyle paddingStyle;
    protected BackgroundStyle bgStyle;
    protected SizeStyle sizeStyle;
    protected BoxShadowStyle boxShadowStyle;

    /**
     * Constructor
     *
     * @param identifier
     */
    public UIBoxComponent(String identifier) {
        super(identifier);
    }

    /**
     * Update style
     */
    @Override
    public void updateStyle() {
        super.updateStyle();
        this.paddingStyle = StyleUtils.getStyleProperty(this, PaddingStyle.class);
        this.bgStyle = StyleUtils.getStyleProperty(this, BackgroundStyle.class);
        this.sizeStyle = StyleUtils.getStyleProperty(this, SizeStyle.class);
        this.boxShadowStyle = StyleUtils.getStyleProperty(this, BoxShadowStyle.class, false);
    }

    /**
     * @param ctx
     * @param delta
     */
    @Override
    public void draw(UIContext ctx, DeltaTime delta) {
        super.draw(ctx, delta);

        RenderEngine2D re = ctx.getRenderEngine();

        // drop shadow?
        if (boxShadowStyle != null && boxShadowStyle.getShadowType().equals(ShadowType.OUTER)) {
            drawBoxShadow(re);
        }

        re.beginPath();
        re.roundedRectangle(bounds, borderStyle.getRadius());

        boolean paint = false;
        boolean fillPaint = false;
        if (bgStyle != null) {
            if (bgStyle instanceof LinearGradientBackgroundStyle) {
                paint = true;
                fillPaint = true;

                var style = ((LinearGradientBackgroundStyle) bgStyle).getGradient();
                re.linearGradient(bounds.getX() + (bounds.getWidth() * style.getStartPosition().getX()),
                        bounds.getY() + (bounds.getHeight() * style.getStartPosition().getY()),
                        bounds.getX() + (bounds.getWidth() * style.getEndPosition().getX()),
                        bounds.getY() + (bounds.getHeight() * style.getEndPosition().getY()),
                        style.getStartColor(), style.getEndColor());

            } else if (bgStyle instanceof BoxGradientBackgroundStyle) {
                paint = true;
                fillPaint = true;

                var style = ((BoxGradientBackgroundStyle) bgStyle).getGradient();
                re.boxGradient(bounds, bounds.getHeight() * style.getRadius(), style.getFeather(),
                        style.getStartColor(), style.getEndColor());

            } else if (bgStyle instanceof SolidBackgroundStyle) {
                paint = true;
                re.fillColor(((SolidBackgroundStyle) bgStyle).getColor());
            }
        }

        re.strokeWidth(borderStyle.getWidth() * 2.0f);
        if (borderStyle.getWidth() > 0 && borderStyle.getColor().getAlpha() > 0) {
            re.strokeColor(borderStyle.getColor());
        }

        re.stroke();
        if (fillPaint) re.fillPaint();
        if (paint) re.fill();
    }

    /**
     * @param re
     */
    private void drawBoxShadow(RenderEngine2D re) {
        re.beginPath();

        float spread = boxShadowStyle.getSpread() + borderStyle.getWidth();
        float blur = boxShadowStyle.getBlur() + borderStyle.getWidth();

        re.boxGradient(
                bounds.getX() + boxShadowStyle.getHorizontalOffset() - spread * 0.5f,
                bounds.getY() + boxShadowStyle.getVerticalOffset() - spread * 0.5f,
                bounds.getWidth() + spread,
                bounds.getHeight() + spread,
                borderStyle.getRadius(), blur,
                boxShadowStyle.getColor(), Color.TRANSPARENT);

        re.roundedRectangle(bounds.getX() + boxShadowStyle.getHorizontalOffset() - spread,
                bounds.getY() + boxShadowStyle.getVerticalOffset() - spread,
                bounds.getWidth() + spread * 2.0f,
                bounds.getHeight() + spread * 2.0f, borderStyle.getRadius());

        re.setSolidity(Solidity.HOLE);
        re.fillPaint();
        re.fill();
    }
}
