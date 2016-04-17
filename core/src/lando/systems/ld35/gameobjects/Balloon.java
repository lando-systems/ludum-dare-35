package lando.systems.ld35.gameobjects;

import aurelienribon.tweenengine.BaseTween;
import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenCallback;
import aurelienribon.tweenengine.primitives.MutableFloat;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import lando.systems.ld35.screens.GameScreen;
import lando.systems.ld35.utils.Assets;
import lando.systems.ld35.utils.LevelBoundry;
import lando.systems.ld35.utils.SoundManager;

/**
 * Created by Doug on 4/16/2016.
 */
public class Balloon {
    public enum State {NORMAL, LIFT, HEAVY, SPINNER, MAGNET, BUZZSAW}

    public static ObjectMap<State, Animation> stateToAnimationMap;

    public static final float ANIM_DURATION = 0.5f;
    public static float MAX_SPEED = 100f;

    public Vector2       position;
    public Vector2       velocity;
    public State         currentState;
    public TextureRegion currentTexture;
    public boolean       animating;
    public MutableFloat  animationTimer;
    public Animation     currentAnimation;
    GameScreen level;
    Rectangle bounds;
    Rectangle intersectorRectangle;
    public Pixmap tilePixmap;
    boolean[] intersectMap;

    public Balloon(Vector2 position, GameScreen screen){
        this.level = screen;
        this.currentState = State.NORMAL;
        this.position = position;
        this.velocity = new Vector2(0, 100);
        this.currentTexture = Assets.balloonTexture;
        this.animating = false;
        this.animationTimer = new MutableFloat(0);
        this.currentAnimation = Assets.balloonToBalloonAnimation;
        this.bounds = new Rectangle(position.x, position.y, 32, 32);
        this.intersectorRectangle = new Rectangle();
        this.intersectMap = new boolean[32*32];

        if (stateToAnimationMap == null) {
            stateToAnimationMap = new ObjectMap<State, Animation>();
            stateToAnimationMap.put(State.NORMAL,  Assets.balloonToBalloonAnimation);
            stateToAnimationMap.put(State.LIFT,    Assets.balloonToRocketAnimation);
            stateToAnimationMap.put(State.HEAVY,   Assets.balloonToWeightAnimation);
            stateToAnimationMap.put(State.SPINNER, Assets.balloonToTorusAnimation);
            stateToAnimationMap.put(State.MAGNET,  Assets.balloonToMagnetAnimation);
            stateToAnimationMap.put(State.BUZZSAW, Assets.balloonToBuzzsawAnimation);
        }
    }

    public void changeState(State state) {
        SoundManager.playBalloonSound(currentState);
        currentState = state;

        // Tween animation from 'previous' state to 'balloon'
        animating = true;
        animationTimer.setValue(currentAnimation.getAnimationDuration());
        Tween.to(animationTimer, -1, ANIM_DURATION / 2f)
                .target(0f)
                .setCallback(new TweenCallback() {
                    @Override
                    public void onEvent(int type, BaseTween<?> source) {
                        // Tween animation from 'balloon' to new state
                        currentAnimation = stateToAnimationMap.get(currentState);
                        Tween.to(animationTimer, -1, ANIM_DURATION / 2f)
                             .target(currentAnimation.getAnimationDuration())
                             .setCallback(new TweenCallback() {
                                 @Override
                                 public void onEvent(int type, BaseTween<?> source) {
                                     animating = false;
                                     setTextureForCurrentState();
                                 }
                             })
                             .start(Assets.tween);
                    }
                })
                .start(Assets.tween);
    }

    public void update(float dt, LevelInfo level){
        switch (currentState){
            case LIFT:
                velocity.y += 100 * dt;
                break;
            case HEAVY:
                velocity.y -= 100 * dt;
                break;
        }

        velocity.y = MathUtils.clamp(velocity.y, -MAX_SPEED, MAX_SPEED);

        // TODO magnets

        // TODO wind
        if (position.y > 200 && position.y < 300){
            velocity.x += 40 * dt;
        }

        velocity.x = MathUtils.clamp(velocity.x, -MAX_SPEED, MAX_SPEED);


        Vector2 nextPos = position.cpy().add(velocity.cpy().scl(dt));
        velocity.scl(.99f);

        // TODO: Do collision detection against level.getTiles
        boolean collided = false;
        Vector2 massOfCollision = new Vector2();
        int tileX = (int)(nextPos.x / 32);
        int tileY = (int)(nextPos.y / 32);
        Array<LevelBoundry> cells = level.getTiles(tileX -1, tileY -1, tileX + 1, tileY + 1);
        if (cells.size > 0){
            bounds.x = nextPos.x;
            bounds.y = nextPos.y;
            for (LevelBoundry boundry: cells){
                if (Intersector.intersectRectangles(boundry.rect, bounds, intersectorRectangle)){
                    if (tilePixmap == null) {
                        Texture t = boundry.tile.getTile().getTextureRegion().getTexture();
                        if (!t.getTextureData().isPrepared()) {
                            t.getTextureData().prepare();
                        }
                        tilePixmap = t.getTextureData().consumePixmap();
                    }

                    Rectangle textureArea = new Rectangle(intersectorRectangle.x - boundry.rect.x + boundry.tile.getTile().getTextureRegion().getRegionX(),
                                                      intersectorRectangle.y - boundry.rect.y + boundry.tile.getTile().getTextureRegion().getRegionY(),
                                                      intersectorRectangle.width, intersectorRectangle.height);

                    int regionX = boundry.tile.getTile().getTextureRegion().getRegionX();
                    // This may need to be <=
                    for (int x = 0; x <= textureArea.width; x++){
                        for (int y = 0; y <=  textureArea.height; y++){
                            int texX = x + (int)textureArea.x;
                            int texY = y + (int)textureArea.y;
                            int pix = tilePixmap.getPixel(texX, tilePixmap.getHeight() - 1 - texY);
                            int index = (int)( intersectorRectangle.x - bounds.x) + x + (int)(intersectorRectangle.y - bounds.y + y) * 32;
//                            if (index >= intersectMap.length) continue;
                            intersectMap[index] = (pix & 0xFF) != 0x00;
                        }
                    }
//                    Gdx.app.log("Collision", textureArea.toString());
                }

            }
            for (int i = 0; i < intersectMap.length;i++){
                if (intersectMap[i]){
                    collided = true;
                    int x = 16 - (i % 32);
                    int y = 16 - (i / 32);
                    massOfCollision.add(x, y);
                }
            }
        }
        if (collided){
            massOfCollision.nor();
            float mag = velocity.len();
            velocity = (massOfCollision.scl(mag * .5f));
            nextPos = position;
//            Gdx.app.log("Collision", "X:" + massOfCollision.x + " Y:" + massOfCollision.y);

        }

        for(int i =0; i < intersectMap.length; i++){
            intersectMap[i] = false;
        }

        position = nextPos;



    }

    public void render(SpriteBatch batch){
        if (animating && currentAnimation != null) {
            batch.draw(currentAnimation.getKeyFrame(animationTimer.floatValue()), position.x, position.y, 32, 32);
        } else {
            batch.draw(currentTexture, position.x, position.y, 32, 32);
        }
    }

    // ------------------------------------------------------------------------
    // Private Implementation
    // ------------------------------------------------------------------------

    private void setTextureForCurrentState() {
        switch(currentState){
            case NORMAL: currentTexture = Assets.balloonTexture; break;
            case LIFT:   currentTexture = Assets.rocketTexture; break;
            case HEAVY:  currentTexture = Assets.weightTexture; break;
            default:     currentTexture = Assets.testTexture; break;
        }
    }

}
