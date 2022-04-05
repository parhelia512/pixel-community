package org.pixel.ext.tiled.content;

import org.pixel.ext.tiled.view.TiledGenericObjectGroupView;

/**
 * A Rectangle TiledObject.
 */
public class TiledRectangle extends TiledObject {
    private double width;
    private double height;

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    @Override
    public void draw(TiledObjectGroup group, TiledGenericObjectGroupView view) {
        view.draw(this, group);
    }
}
