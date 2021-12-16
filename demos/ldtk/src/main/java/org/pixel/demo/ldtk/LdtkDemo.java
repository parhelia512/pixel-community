package org.pixel.demo.ldtk;

import org.pixel.commons.DeltaTime;
import org.pixel.commons.logger.ConsoleLogger;
import org.pixel.commons.logger.LogLevel;
import org.pixel.content.ContentManager;
import org.pixel.core.Camera2D;
import org.pixel.core.PixelWindow;
import org.pixel.core.WindowSettings;
import org.pixel.ext.ldtk.GameLevel;
import org.pixel.ext.ldtk.GameWorld;
import org.pixel.ext.ldtk.GameWorldFactory;
import org.pixel.graphics.render.SpriteBatch;
import org.pixel.input.keyboard.Keyboard;
import org.pixel.input.keyboard.KeyboardKey;

public class LdtkDemo extends PixelWindow {

    private static final float CAMERA_SPEED = 500f;

    private SpriteBatch spriteBatch;
    private ContentManager contentManager;
    private Camera2D gameCamera;

    private GameWorld gameWorld;
    private GameLevel activeLevel;

    private TitleFpsCounter titleFpsCounter;

    /**
     * Constructor.
     *
     * @param settings The settings to use.
     */
    public LdtkDemo(WindowSettings settings) {
        super(settings);
    }

    @Override
    public void load() {
        titleFpsCounter = new TitleFpsCounter(this);
        spriteBatch = new SpriteBatch();
        contentManager = new ContentManager();
        gameCamera = new Camera2D(this);
        gameCamera.setOrigin(0, 0);
        gameCamera.setZoom(1f);
        gameWorld = GameWorldFactory.load("pixel-demo-advanced.ldtk", contentManager);
        activeLevel = gameWorld.getLevel("Level_0");
    }

    @Override
    public void update(DeltaTime delta) {
        if (Keyboard.isKeyDown(KeyboardKey.W) || Keyboard.isKeyDown(KeyboardKey.UP)) {
            gameCamera.translate(0, -CAMERA_SPEED * delta.getElapsed());
        } else if (Keyboard.isKeyDown(KeyboardKey.S) || Keyboard.isKeyDown(KeyboardKey.DOWN)) {
            gameCamera.translate(0, CAMERA_SPEED * delta.getElapsed());
        }

        if (Keyboard.isKeyDown(KeyboardKey.A) || Keyboard.isKeyDown(KeyboardKey.LEFT)) {
            gameCamera.translate(-CAMERA_SPEED * delta.getElapsed(), 0);
        } else if (Keyboard.isKeyDown(KeyboardKey.D) || Keyboard.isKeyDown(KeyboardKey.RIGHT)) {
            gameCamera.translate(CAMERA_SPEED * delta.getElapsed(), 0);
        }

        if (Keyboard.isKeyDown(KeyboardKey.E)) {
            gameCamera.setZoom(gameCamera.getZoom() + 0.5f * delta.getElapsed());
        } else if (Keyboard.isKeyDown(KeyboardKey.Q)) {
            gameCamera.setZoom(gameCamera.getZoom() - 0.5f * delta.getElapsed());
        }

        if (Keyboard.isKeyDown(KeyboardKey.D_1)) {
            gameCamera.setZoom(1f);
        } else if (Keyboard.isKeyDown(KeyboardKey.D_2)) {
            gameCamera.setZoom(2f);
        } else if (Keyboard.isKeyDown(KeyboardKey.D_3)) {
            gameCamera.setZoom(3f);
        }

        titleFpsCounter.update(delta);
    }

    @Override
    public void draw(DeltaTime delta) {
        if (activeLevel.getBackgroundColor() != null) {
            setBackgroundColor(activeLevel.getBackgroundColor());
        }

        spriteBatch.begin(gameCamera.getViewMatrix());

        for (GameLevel level : gameWorld.getLevels()) {
            level.draw(delta, spriteBatch);
        }

        spriteBatch.end();
    }

    @Override
    public void dispose() {
        super.dispose();
        spriteBatch.dispose();
        contentManager.dispose();
    }

    public static void main(String[] args) {
        final int width = 800;
        final int height = 600;
        WindowSettings settings = new WindowSettings(width, height);
        settings.setWindowTitle("LDTK Demo");
        settings.setWindowResizable(true);
        settings.setMultisampling(2);
        settings.setVsync(true);
        settings.setDebugMode(true);
        settings.setWindowWidth(width);
        settings.setWindowHeight(height);

        ConsoleLogger.setLogLevel(LogLevel.TRACE);

        PixelWindow window = new LdtkDemo(settings);
        window.start();
    }
}
