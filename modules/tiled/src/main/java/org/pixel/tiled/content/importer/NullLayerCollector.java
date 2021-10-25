package org.pixel.tiled.content.importer;

import org.pixel.tiled.content.Layer;
import org.pixel.tiled.content.TileMap;
import org.w3c.dom.Element;

public class NullLayerCollector extends LayerCollector {
    @Override
    public Layer process(TileMap tileMap, Element tileLayerElement) {
        return null;
    }
}
