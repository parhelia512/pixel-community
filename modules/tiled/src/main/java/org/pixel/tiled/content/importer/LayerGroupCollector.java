package org.pixel.tiled.content.importer;

import org.pixel.tiled.content.Layer;
import org.pixel.tiled.content.LayerGroup;
import org.pixel.tiled.content.TileMap;
import org.w3c.dom.Element;

public class LayerGroupCollector extends LayerCollector {
    LayerProcessor processor;

    public LayerGroupCollector() {
        this.processor = new LayerProcessor();
    }

    public LayerGroupCollector(LayerProcessor processor) {
        this.processor = processor;
    }

    @Override
    Layer process(TileMap tileMap, Element tileLayerElement) {
        LayerGroup group = new LayerGroup(collectLayerData(tileMap, tileLayerElement));

        group.setLayers(processor.processChildren(tileMap, tileLayerElement));

        return group;
    }
}
