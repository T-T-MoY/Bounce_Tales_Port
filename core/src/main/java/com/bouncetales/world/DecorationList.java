package com.bouncetales.world;

/**
 * Lista de sprites decorativos de una entidad (tag 9, clase {@code s}).
 * Las posiciones son coordenadas locales en unidades de mundo (no 16.16).
 */
public final class DecorationList {

    private static final int ANIMATION_NONE = -1;
    private static final int ANIMATION_BREAK = 474;
    private static final int ANIMATION_BUBBLE = 485;
    private static final int ANIMATION_SWAY = 480;
    private static final float FRAME_TIME = 0.15f;
    private static final float BREAK_TIME = 0.75f;
    private static final float BREAK_FADE_TIME = 1.5f;

    public final int[] spriteIds;
    public final float[] x;
    public final float[] y;

    private final int[] originalSpriteIds;
    private final int[] animationIds;
    private final float[] animationTimes;

    public boolean hidden;

    public DecorationList(int[] spriteIds, float[] x, float[] y) {
        this.spriteIds = spriteIds;
        this.x = x;
        this.y = y;
        this.originalSpriteIds = spriteIds.clone();
        this.animationIds = new int[spriteIds.length];
        this.animationTimes = new float[spriteIds.length];
        reset();
    }

    public int size() {
        return spriteIds.length;
    }

    public void startAnimation() {
        hidden = false;
        for (int i = 0; i < originalSpriteIds.length; i++) {
            animationIds[i] = animationFor(originalSpriteIds[i]);
            animationTimes[i] = 0f;
            spriteIds[i] = originalSpriteIds[i];
        }
    }

    public void update(float dt) {
        for (int i = 0; i < animationIds.length; i++) {
            if (animationIds[i] == ANIMATION_NONE) {
                continue;
            }
            animationTimes[i] += dt;
            if (animationIds[i] == ANIMATION_BREAK
                    && animationTimes[i] >= BREAK_TIME + BREAK_FADE_TIME) {
                hidden = true;
            }
        }
    }

    public int animationId(int index) {
        return animationIds[index];
    }

    public int animationFrame(int index, int frameCount) {
        if (frameCount <= 1) {
            return 0;
        }
        float elapsed = animationTimes[index];
        if (animationIds[index] == ANIMATION_BREAK) {
            return Math.min(frameCount - 1, (int) (Math.min(elapsed, BREAK_TIME) / FRAME_TIME));
        }
        return (int) (elapsed / FRAME_TIME) % frameCount;
    }

    public float opacity(int index) {
        if (animationIds[index] != ANIMATION_BREAK || animationTimes[index] <= BREAK_TIME) {
            return 1f;
        }
        return Math.max(0f, 1f - (animationTimes[index] - BREAK_TIME) / BREAK_FADE_TIME);
    }

    public void reset() {
        hidden = false;
        for (int i = 0; i < originalSpriteIds.length; i++) {
            animationIds[i] = ANIMATION_NONE;
            animationTimes[i] = 0f;
            spriteIds[i] = originalSpriteIds[i];
        }
    }

    private static int animationFor(int spriteId) {
        switch (spriteId) {
            case 334:
                return ANIMATION_BREAK;
            case 118:
                return ANIMATION_BUBBLE;
            case 342:
                return ANIMATION_SWAY;
            default:
                return ANIMATION_NONE;
        }
    }
}
