package org.pixel.ext.ldtk;

import java.util.Collection;
import java.util.Map;
import lombok.Builder;

@Builder
public class GameWorld {

    private final Map<String, GameLevel> levelMap;

    /**
     * Get the level names of this world.
     *
     * @return The level names of this world.
     */
    public Collection<String> getLevelNames() {
        return levelMap.keySet();
    }

    /**
     * Get the levels of this world.
     *
     * @return The levels of this world.
     */
    public Collection<GameLevel> getLevels() {
        return levelMap.values();
    }

    /**
     * Get the level map.
     *
     * @param levelName The name of the level.
     * @return The level map.
     */
    public GameLevel getLevel(String levelName) {
        return levelMap.get(levelName);
    }
}
