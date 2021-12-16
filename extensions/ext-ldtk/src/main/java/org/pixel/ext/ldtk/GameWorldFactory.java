package org.pixel.ext.ldtk;

import io.github.joafalves.ldtk.LdtkConverter;
import io.github.joafalves.ldtk.model.LayerInstance;
import io.github.joafalves.ldtk.model.Level;
import io.github.joafalves.ldtk.model.Project;
import io.github.joafalves.ldtk.model.TileInstance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.pixel.commons.logger.Logger;
import org.pixel.commons.logger.LoggerFactory;
import org.pixel.commons.util.IOUtils;
import org.pixel.content.ContentManager;
import org.pixel.content.Texture;
import org.pixel.graphics.Color;
import org.pixel.math.Rectangle;
import org.pixel.math.Vector2;

public class GameWorldFactory {

    private static final Logger log = LoggerFactory.getLogger(GameWorldFactory.class);

    /**
     * Creates a new world based on the given LDTK JSON source file.
     *
     * @param jsonFilePath   The path to the json file.
     * @param contentManager The content manager to load the assets.
     * @return The new world or null if the file could not be read.
     */
    public static GameWorld load(String jsonFilePath, ContentManager contentManager) {
        log.trace("Loading world from file: '{}'.", jsonFilePath);

        try {
            var jsonString = IOUtils.loadFileString(jsonFilePath);
            if (jsonString == null) {
                log.warn("Could not load json data from file: '{}'.", jsonFilePath);
                return null;
            }

            return processData(LdtkConverter.fromJsonString(jsonString), contentManager);

        } catch (IOException e) {
            log.error("Failed to load world from file: '{}'.", jsonFilePath, e);
        }

        return null;
    }

    /**
     * Creates a new world based on the given LDTK Project.
     *
     * @param project        The LDTK Project.
     * @param contentManager The content manager to load the assets.
     * @return The new world.
     */
    private static GameWorld processData(Project project, ContentManager contentManager) {
        HashMap<String, GameLevel> levels = new HashMap<>();
        for (Level level : project.getLevels()) {
            log.trace("Processing level: '{}'.", level.getIdentifier());

            GameLevel.GameLevelBuilder gameLevelBuilder = GameLevel.builder();
            gameLevelBuilder.name(level.getIdentifier());
            gameLevelBuilder.worldPosition(new Vector2(level.getWorldX(), level.getWorldY()));

            processLevelBackground(gameLevelBuilder, level, contentManager); // background
            processLevelLayers(gameLevelBuilder, level, contentManager); // layers

            levels.put(level.getIdentifier(), gameLevelBuilder.build());
        }

        return GameWorld.builder()
                .levelMap(levels)
                .build();
    }

    /**
     * Processes the background of the level.
     *
     * @param builder        The builder to add the background to.
     * @param level          The level to process.
     * @param contentManager The content manager to load the assets.
     */
    private static void processLevelBackground(GameLevel.GameLevelBuilder builder, Level level,
            ContentManager contentManager) {

        Texture backgroundTexture = null;
        if (level.getBgRelPath() != null) {
            backgroundTexture = contentManager.load(level.getBgRelPath(), Texture.class);
        }

        Rectangle backgroundCropArea = null;
        Rectangle backgroundDisplayArea = null;
        if (backgroundTexture != null && level.getBgPos() != null) {
            var bgPos = level.getBgPos();
            backgroundCropArea = new Rectangle(bgPos.getCropRect().get(0).floatValue(),
                    bgPos.getCropRect().get(1).floatValue(), bgPos.getCropRect().get(2).floatValue(),
                    bgPos.getCropRect().get(3).floatValue());
            backgroundDisplayArea = new Rectangle(
                    level.getWorldX() + bgPos.getTopLeftPx().get(0).floatValue(),
                    level.getWorldY() + bgPos.getTopLeftPx().get(1).floatValue(),
                    backgroundTexture.getWidth() * bgPos.getScale().get(0).floatValue(),
                    backgroundTexture.getHeight() * bgPos.getScale().get(1).floatValue());
        }

        builder.backgroundColor(Color.fromString(level.getBgColor()))
                .backgroundTexture(backgroundTexture)
                .backgroundDisplayArea(backgroundDisplayArea)
                .backgroundCropArea(backgroundCropArea);
    }

    /**
     * Processes the layers of the level.
     *
     * @param builder        The builder to add the layers to.
     * @param level          The level to process.
     * @param contentManager The content manager to load the assets.
     */
    private static void processLevelLayers(GameLevel.GameLevelBuilder builder, Level level,
            ContentManager contentManager) {
        if (level.getLayerInstances() == null) {
            return; // no layers
        }

        List<GameLayer> layerList = new ArrayList<>();
        for (LayerInstance layerInstance : level.getLayerInstances()) {
            List<TileInstance> tileList = new ArrayList<>();
            if (layerInstance.getGridTiles() != null && !layerInstance.getGridTiles().isEmpty()) {
                tileList.addAll(layerInstance.getGridTiles());
            }
            if (layerInstance.getAutoLayerTiles() != null && !layerInstance.getAutoLayerTiles().isEmpty()) {
                tileList.addAll(layerInstance.getAutoLayerTiles());
            }

            if (!tileList.isEmpty()) {
                List<GameLayerTile> gameLayerTileList = new ArrayList<>();
                for (TileInstance gridTile : tileList) {
                    gameLayerTileList.add(processTileInstance(gridTile, layerInstance, level));
                }

                Texture layerTilesetTexture = null;
                if (layerInstance.getTilesetRelPath() != null) {
                    layerTilesetTexture = contentManager.load(layerInstance.getTilesetRelPath(), Texture.class);
                }

                layerList.add(0, GameLayer.builder()
                        .name(layerInstance.getIdentifier())
                        .visible(layerInstance.getVisible())
                        .tilesetTexture(layerTilesetTexture)
                        .tileList(gameLayerTileList)
                        .build());
            }
        }

        if (!layerList.isEmpty()) {
            builder.gameLayerList(layerList);
        }
    }

    /**
     * Process a single tile instance.
     *
     * @param tileInstance  The tile instance to process.
     * @param layerInstance The layer instance the tile instance belongs to.
     * @param level         The level the tile instance belongs to.
     * @return The processed tile instance.
     */
    private static GameLayerTile processTileInstance(TileInstance tileInstance, LayerInstance layerInstance,
            Level level) {
        return GameLayerTile.builder()
                .tilesetSource(processTileSourceArea(tileInstance, layerInstance))
                .displayArea(
                        new Rectangle(level.getWorldX() + tileInstance.getPx().get(0).floatValue() - 0.5f,
                                level.getWorldY() + tileInstance.getPx().get(1).floatValue() - 0.5f,
                                layerInstance.getGridSize() + 1f,
                                layerInstance.getGridSize() + 1f))
                .position(
                        new Vector2(level.getWorldX() + tileInstance.getPx().get(0).floatValue(),
                                level.getWorldY() + tileInstance.getPx().get(1).floatValue()))
                .build();
    }

    /**
     * Process the source area of a tile instance.
     *
     * @param tileInstance  The tile instance to process.
     * @param layerInstance The layer instance the tile instance belongs to.
     * @return The processed source area.
     */
    private static Rectangle processTileSourceArea(TileInstance tileInstance, LayerInstance layerInstance) {
        float sx = tileInstance.getSrc().get(0).floatValue();
        float sy = tileInstance.getSrc().get(1).floatValue();
        float gridSize = layerInstance.getGridSize();

        switch ((int) tileInstance.getF()) {
            case 1: // flip horizontally
                return new Rectangle(sx + (sx + gridSize - sx), sy, -gridSize, gridSize);

            case 2: // flip vertically
                return new Rectangle(sx, sy + (sy + gridSize - sy), gridSize, -gridSize);

            case 3: // flip both
                return new Rectangle(sx + (sx + gridSize - sx), sy + (sy + gridSize - sy), -gridSize, -gridSize);

            default: // no flip
                return new Rectangle(sx, sy, gridSize, gridSize);
        }
    }
}
