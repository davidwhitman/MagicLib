//By Nicke535, handles customizable "fake trails" similar to the vanilla QUAD_STRIP smoke implementation.
//Note that any sprites that use this plugin must have a multiple of 2 as size (so 16, 32, 64, 128 etc.), both in width and height
package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.util.MagicTrailObject;
import data.scripts.util.MagicTrailTracker;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class MagicTrailPlugin extends BaseEveryFrameCombatPlugin {
    //Tracker for unique ID getting. Only use for this script, though: it's dangerous to use for other ID purposes, since it is so simple
    //NOTE: IDs should be bigger than 0; lower than 0 IDs are used by the script for "cut" trails
    private static float usedIDs = 1f;
    private static float usedCutterIDs = -1f;

    //Map which handles all the trails: takes in a render layer, an integer (the texture) and a map of MagicTrailTrackers, identified by a unique ID which must be tracked for each source independently
    protected Map<CombatEngineLayers, Map<Integer, Map<Float, MagicTrailTracker>>> mainMap = new HashMap<>();

    //Map similar to mainMap, but for animated trails
    protected Map<CombatEngineLayers, Map<Float, MagicTrailTracker>> animMap = new HashMap<>();

    //Map for "cutting" trails: if a trail belongs to an entity (it hasn't already been cut) its ID is here
    private Map<CombatEngineLayers, Map<Integer, Map<CombatEntityAPI, List<Float>>>> cuttingMap = new HashMap<>();
    private Map<CombatEngineLayers, Map<CombatEntityAPI, List<Float>>> cuttingMapAnimated = new HashMap<>();

    @Override
    public void init(CombatEngineAPI engine) {
        //Creates the render plugin and adds it to the engine
        MagicTrailRenderer renderer = new MagicTrailRenderer(this);
        engine.addLayeredRenderingPlugin(renderer);

        //Stores our plugins in easy-to-reach locations, so we can access it in different places
        engine.getCustomData().put("MagicTrailPlugin", this);
        engine.getCustomData().put("MagicTrailRenderer", renderer);

        usedIDs = 0f;
        mainMap.clear();
        animMap.clear();
        cuttingMap.clear();
        cuttingMapAnimated.clear();
    }

    //Ticks all maps, and checks for any entity that should recieve library-free cutting
    @Override
    public void advance (float amount, java.util.List<InputEventAPI> events) {
        if (Global.getCombatEngine() == null || Global.getCombatEngine().isPaused()) {
            return;
        }

        //Checks our combat engine's customData and sees if someone has attempted a library-free trail cutting this frame
        //If they have, cut it properly and store the fact that cutting took place this frame
        boolean cutSomethingThisFrame = false;
        for (String key : Global.getCombatEngine().getCustomData().keySet()) {
            if (key.contains("MagicTrailPlugin_LIB_FREE_TRAIL_CUT")) {
                if (Global.getCombatEngine().getCustomData().get(key) instanceof CombatEntityAPI) {
                    cutSomethingThisFrame = true;
                    cutTrailsOnEntity((CombatEntityAPI)Global.getCombatEngine().getCustomData().get(key));
                }
            }
        }

        //If cutting took place this frame, we remove all CustomData key-data pairs that are used for lib-free cutting
        if (cutSomethingThisFrame) {
            Set<String> keySet = Global.getCombatEngine().getCustomData().keySet();
            for (String key : keySet) {
                if (key.contains("MagicTrailPlugin_LIB_FREE_TRAIL_CUT")) {
                    if (Global.getCombatEngine().getCustomData().get(key) instanceof CombatEntityAPI){
                        Global.getCombatEngine().getCustomData().remove(key);
                    }
                }
            }
        }

        //Ticks the main map
        for (CombatEngineLayers layer : mainMap.keySet()) {
            for (Integer texID : mainMap.get(layer).keySet()) {
                for (Float ID : mainMap.get(layer).get(texID).keySet()) {
                    mainMap.get(layer).get(texID).get(ID).tickTimersInTrail(amount);
                    mainMap.get(layer).get(texID).get(ID).clearAllDeadObjects();
                }
            }
        }

        //Ticks the animated map
        for (CombatEngineLayers layer : animMap.keySet()) {
            for (Float ID : animMap.get(layer).keySet()) {
                animMap.get(layer).get(ID).tickTimersInTrail(amount);
                animMap.get(layer).get(ID).clearAllDeadObjects();
            }
        }
    }

    /**
     * Spawns a trail piece, which links up with other pieces with the same ID
     * to form a smooth trail. This function has most of the functionality you
     * need; should you want more configurability, use AddTrailMemberAdvanced
     * instead
     *
     * @param linkedEntity The entity this trail is attached to, used for cutting trails.
     *                     Can be Null, but that should really only be done in weird, edge-case scenarios
     * @param ID The ID for this specific trail. Preferably get this from getUniqueID,
     *           but it's not required: just expect very weird results if you don't
     * @param sprite Which sprite to draw for this trail: do *not* change this halfway through a trail,
     *               as that will split it into two trails
     * @param position Starting position for this piece of trail
     * @param speed The speed, in SU, this trail piece is moving at
     * @param angle Which angle this piece of trail has in degrees; determines which direction it moves,
     *              and which direction its size is measured over
     * @param startSize The starting size (or rather width) this piece of trail has. Measured in SU. A trail
     *                  smoothly transitions from its startSize to its endSize over its duration
     * @param endSize The ending size (or rather width) this trail piece has. Measured in SU. A trail smoothly
     *                transitions from its startSize to its endSize over its duration
     * @param color The color of this piece of trail. Can be changed in the middle of a trail, and will blend
     *              smoothly between pieces. Ignores alpha component entirely
     * @param opacity The starting opacity of this piece of trail. Is a value between 0f and 1f. The opacity
     *                gradually approaches 0f over the trail's duration
     * @param duration The duration of the trail, in seconds
     * @param additive Whether this trail will use additive blending or not. Does not support being changed in
     *                 the middle of a trail
     * @param offsetVelocity The offset velocity of the trail; this is an additional velocity that is
     *                       unaffected by rotation and facing, and will never change over the trail's lifetime
     */
    public static void AddTrailMemberSimple (CombatEntityAPI linkedEntity, float ID, SpriteAPI sprite, Vector2f position, float speed, float angle, float startSize, float endSize, Color color,
                                             float opacity, float duration, boolean additive, Vector2f offsetVelocity) {
        AddTrailMemberSimple(linkedEntity, ID, sprite, position, speed, angle, startSize, endSize, color, opacity, duration, additive, offsetVelocity, CombatEngineLayers.CONTRAILS_LAYER);
    }

    /**
     * Spawns a trail piece, which links up with other pieces with the same ID
     * to form a smooth trail. This function has most of the functionality you
     * need; should you want more configurability, use AddTrailMemberAdvanced
     * instead
     *
     * @param linkedEntity The entity this trail is attached to, used for cutting trails.
     *                     Can be Null, but that should really only be done in weird, edge-case scenarios
     * @param ID The ID for this specific trail. Preferably get this from getUniqueID,
     *           but it's not required: just expect very weird results if you don't
     * @param sprite Which sprite to draw for this trail: do *not* change this halfway through a trail,
     *               as that will split it into two trails
     * @param position Starting position for this piece of trail
     * @param speed The speed, in SU, this trail piece is moving at
     * @param angle Which angle this piece of trail has in degrees; determines which direction it moves,
     *              and which direction its size is measured over
     * @param startSize The starting size (or rather width) this piece of trail has. Measured in SU. A trail
     *                  smoothly transitions from its startSize to its endSize over its duration
     * @param endSize The ending size (or rather width) this trail piece has. Measured in SU. A trail smoothly
     *                transitions from its startSize to its endSize over its duration
     * @param color The color of this piece of trail. Can be changed in the middle of a trail, and will blend
     *              smoothly between pieces. Ignores alpha component entirely
     * @param opacity The starting opacity of this piece of trail. Is a value between 0f and 1f. The opacity
     *                gradually approaches 0f over the trail's duration
     * @param duration The duration of the trail, in seconds
     * @param additive Whether this trail will use additive blending or not. Does not support being changed in
     *                 the middle of a trail
     * @param offsetVelocity The offset velocity of the trail; this is an additional velocity that is
     *                       unaffected by rotation and facing, and will never change over the trail's lifetime
     * @param layerToRenderOn Which combat layer to render the trail on. All available layers are specified in
     *                        CombatEngineLayers. Old behaviour was CombatEngineLayers.BELOW_INDICATORS_LAYER.
     *                        CANNOT change mid-trail, under any circumstance
     */
    public static void AddTrailMemberSimple (CombatEntityAPI linkedEntity, float ID, SpriteAPI sprite, Vector2f position, float speed, float angle, float startSize, float endSize, Color color,
                                             float opacity, float duration, boolean additive, Vector2f offsetVelocity, CombatEngineLayers layerToRenderOn) {
        //First, find the plugin
        if (Global.getCombatEngine() == null) {
            return;
        } else if (!(Global.getCombatEngine().getCustomData().get("MagicTrailPlugin") instanceof MagicTrailPlugin)) {
            return;
        }
        MagicTrailPlugin plugin = (MagicTrailPlugin)Global.getCombatEngine().getCustomData().get("MagicTrailPlugin");

        //Finds the correct maps, and ensures they are actually instantiated [and adds our ID to the cutting map]
        int texID = sprite.getTextureId();
        Map<Integer, Map<Float, MagicTrailTracker>> layerMap = plugin.mainMap.get(layerToRenderOn);
        if (layerMap == null) {
            layerMap = new HashMap<>();
            plugin.mainMap.put(layerToRenderOn, layerMap);
        }
        if (layerMap.get(texID) == null) {
            layerMap.put(texID, new HashMap<Float, MagicTrailTracker>());
        }
        if (layerMap.get(texID).get(ID) == null) {
            layerMap.get(texID).put(ID, new MagicTrailTracker());
        }
        if (linkedEntity != null) {
            Map<Integer, Map<CombatEntityAPI, List<Float>>> layerCutMap = plugin.cuttingMap.get(layerToRenderOn);
            if (layerCutMap == null) {
                layerCutMap = new HashMap<>();
                plugin.cuttingMap.put(layerToRenderOn, layerCutMap);
            }
            if (layerCutMap.get(texID) == null) {
                layerCutMap.put(texID, new HashMap<CombatEntityAPI, List<Float>>());
            }
            if (layerCutMap.get(texID).get(linkedEntity) == null) {
                layerCutMap.get(texID).put(linkedEntity, new ArrayList<Float>());
            }
            if (!layerCutMap.get(texID).get(linkedEntity).contains(ID)) {
                layerCutMap.get(texID).get(linkedEntity).add(ID);
            }
        }

        //Converts our additive/non-additive option to true openGL stuff
        int srcBlend = GL_SRC_ALPHA;
        int destBlend = additive ? GL_ONE : GL_ONE_MINUS_SRC_ALPHA;

        //Creates the custom object we want
        MagicTrailObject objectToAdd = new MagicTrailObject(0f, 0f, duration, startSize, endSize, 0f, 0f,
                opacity, srcBlend, destBlend, speed, speed, color, color, angle, position, -1f, offsetVelocity,
                0f, 0);

        //And finally add it to the correct location in our maps
        layerMap.get(texID).get(ID).addNewTrailObject(objectToAdd);
    }

    /**
     * Spawns a trail piece, which links up with other pieces with the same ID
     * to form a smooth trail. This function has all available functions; if you
     * just want to spawn a normal trail without all the extra configuration involved,
     * use AddTrailMemberSimple instead.
     *
     * @param linkedEntity The entity this trail is attached to, used for cutting trails.
     *                     Can be Null, but that should really only be done in weird, edge-case scenarios
     * @param ID The ID for this specific trail. Preferably get this from getUniqueID,
     *           but it's not required: just expect very weird results if you don't
     * @param sprite Which sprite to draw for this trail: do *not* change this halfway through a trail,
     *               as that will split it into two trails
     * @param position Starting position for this piece of trail
     * @param startSpeed The starting speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                   transitions from its startSpeed to its endSpeed over its duration
     * @param endSpeed The ending speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                 transitions from its startSpeed to its endSpeed over its duration
     * @param angle Which angle this piece of trail has in degrees; determines which direction it moves,
     *              and which direction its size is measured over
     * @param startAngularVelocity The angular velocity this trail piece has when spawned. The angular velocity
     *                             of a trail piece smoothly transitions from startAngularVelocity to
     *                             endAngularVelocity over its duration
     * @param endAngularVelocity The angular velocity this trail piece has just before disappearing.
     *                           The angular velocity of a trail piece smoothly transitions from
     *                           startAngularVelocity to endAngularVelocity over its duration
     * @param startSize The starting size (or rather width) this piece of trail has. Measured in SU. A trail
     *                  smoothly transitions from its startSize to its endSize over its duration
     * @param endSize The ending size (or rather width) this trail piece has. Measured in SU. A trail smoothly
     *                transitions from its startSize to its endSize over its duration
     * @param startColor The color this piece of trail has when spawned. Can be changed in the middle of a trail,
     *                   and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                   smoothly transitions from startColor to endColor over its duration
     * @param endColor The color this piece of trail has just before disappearing. Can be changed in the middle of a
     *                 trail, and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                 smoothly transitions from startColor to endColor over its duration
     * @param opacity The starting opacity of this piece of trail. Is a value between 0f and 1f. The opacity
     *                gradually approaches 0f over the trail's duration
     * @param inDuration How long this trail spends "fading in"; for this many seconds, the opacity of the trail piece
     *                   steadily increases until reaching "opacity". A trail's total duration is
     *                   inDuration + mainDuration + outDuration
     * @param mainDuration How long a trail uses its maximum opacity. A trail's total duration is
     *                     inDuration + mainDuration + outDuration
     * @param outDuration How long a trail spends "fading out"; over this many seconds at the end of the trail's
     *                    duration, its opacity goes from "opacity" to 0f. A trail's total duration is
     *                    inDuration + mainDuration + outDuration
     * @param blendModeSRC Which SRD openGL blend mode to use for the trail. If you are unsure of what this means, just
     *                     put it as GL_SRC_ALPHA
     * @param blendModeDEST Which DEST openGL blend mode to use for the trail. If you are unsure of what this means,
     *                      put it as GL_ONE_MINUS_SRC_ALPHA for normal blending and GL_ONE for additive blending
     * @param textureLoopLength How many SU it takes for the texture to loop. Should preferably be non-zero. If the
     *                          trail is not supposed to loop, put this as -1f
     * @param textureScrollSpeed How fast, and in which direction, the texture scrolls over the trail. Defined so that
     *                           1000 means scrolling the entire texture length once per second, and 2000 means
     *                           scrolling the entire texture length twice per second
     * @param offsetVelocity The offset velocity of the trail; this is an additional velocity that is
     *                       unaffected by rotation and facing, and will never change over the trail's lifetime
     * @param advancedOptions The most unique and special options go in a special Map<> here. Be careful to input the
     *                        correct type values and use the right data keys. Any new features will be added here to
     *                        keep compatibility with old mod versions. Can be null. Currently supported keys:
     *                        "SIZE_PULSE_WIDTH" :  Float - How much additional width the trail gains each "pulse", at
     *                                              most. Used in conjunction with SIZE_PULSE_COUNT
     *                        "SIZE_PULSE_COUNT" :  Integer - How many times the trail "pulses" its width over its
     *                                              lifetime. Used in conjunction with SIZE_PULSE_WIDTH
     *                        "FORWARD_PROPAGATION" :  Boolean - If the trail uses the legacy render method of
     *                                                 "forward propagation". Used to be the default. CANNOT be
     *                                                 changed mid-trail
     */
    public static void AddTrailMemberAdvanced (CombatEntityAPI linkedEntity, float ID, SpriteAPI sprite, Vector2f position, float startSpeed, float endSpeed, float angle,
                                               float startAngularVelocity, float endAngularVelocity, float startSize, float endSize, Color startColor, Color endColor, float opacity,
                                               float inDuration, float mainDuration, float outDuration, int blendModeSRC, int blendModeDEST, float textureLoopLength, float textureScrollSpeed,
                                               Vector2f offsetVelocity, @Nullable Map<String,Object> advancedOptions) {
        AddTrailMemberAdvanced(linkedEntity, ID, sprite, position, startSpeed, endSpeed, angle, startAngularVelocity, endAngularVelocity, startSize, endSize,
                startColor, endColor, opacity, inDuration, mainDuration, outDuration, blendModeSRC, blendModeDEST, textureLoopLength, textureScrollSpeed,
                offsetVelocity, advancedOptions, CombatEngineLayers.CONTRAILS_LAYER, 1f);
    }

    /**
     * Spawns a trail piece, which links up with other pieces with the same ID
     * to form a smooth trail. This function has all available functions; if you
     * just want to spawn a normal trail without all the extra configuration involved,
     * use AddTrailMemberSimple instead.
     *
     * @param linkedEntity The entity this trail is attached to, used for cutting trails.
     *                     Can be Null, but that should really only be done in weird, edge-case scenarios
     * @param ID The ID for this specific trail. Preferably get this from getUniqueID,
     *           but it's not required: just expect very weird results if you don't
     * @param sprite Which sprite to draw for this trail: do *not* change this halfway through a trail,
     *               as that will split it into two trails
     * @param position Starting position for this piece of trail
     * @param startSpeed The starting speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                   transitions from its startSpeed to its endSpeed over its duration
     * @param endSpeed The ending speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                 transitions from its startSpeed to its endSpeed over its duration
     * @param angle Which angle this piece of trail has in degrees; determines which direction it moves,
     *              and which direction its size is measured over
     * @param startAngularVelocity The angular velocity this trail piece has when spawned. The angular velocity
     *                             of a trail piece smoothly transitions from startAngularVelocity to
     *                             endAngularVelocity over its duration
     * @param endAngularVelocity The angular velocity this trail piece has just before disappearing.
     *                           The angular velocity of a trail piece smoothly transitions from
     *                           startAngularVelocity to endAngularVelocity over its duration
     * @param startSize The starting size (or rather width) this piece of trail has. Measured in SU. A trail
     *                  smoothly transitions from its startSize to its endSize over its duration
     * @param endSize The ending size (or rather width) this trail piece has. Measured in SU. A trail smoothly
     *                transitions from its startSize to its endSize over its duration
     * @param startColor The color this piece of trail has when spawned. Can be changed in the middle of a trail,
     *                   and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                   smoothly transitions from startColor to endColor over its duration
     * @param endColor The color this piece of trail has just before disappearing. Can be changed in the middle of a
     *                 trail, and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                 smoothly transitions from startColor to endColor over its duration
     * @param opacity The starting opacity of this piece of trail. Is a value between 0f and 1f. The opacity
     *                gradually approaches 0f over the trail's duration
     * @param inDuration How long this trail spends "fading in"; for this many seconds, the opacity of the trail piece
     *                   steadily increases until reaching "opacity". A trail's total duration is
     *                   inDuration + mainDuration + outDuration
     * @param mainDuration How long a trail uses its maximum opacity. A trail's total duration is
     *                     inDuration + mainDuration + outDuration
     * @param outDuration How long a trail spends "fading out"; over this many seconds at the end of the trail's
     *                    duration, its opacity goes from "opacity" to 0f. A trail's total duration is
     *                    inDuration + mainDuration + outDuration
     * @param blendModeSRC Which SRD openGL blend mode to use for the trail. If you are unsure of what this means, just
     *                     put it as GL_SRC_ALPHA
     * @param blendModeDEST Which DEST openGL blend mode to use for the trail. If you are unsure of what this means,
     *                      put it as GL_ONE_MINUS_SRC_ALPHA for normal blending and GL_ONE for additive blending
     * @param textureLoopLength How many SU it takes for the texture to loop. Should preferably be non-zero. If the
     *                          trail is not supposed to loop, put this as -1f
     * @param textureScrollSpeed How fast, and in which direction, the texture scrolls over the trail. Defined so that
     *                           1000 means scrolling the entire texture length once per second, and 2000 means
     *                           scrolling the entire texture length twice per second
     * @param offsetVelocity The offset velocity of the trail; this is an additional velocity that is
     *                       unaffected by rotation and facing, and will never change over the trail's lifetime
     * @param advancedOptions The most unique and special options go in a special Map<> here. Be careful to input the
     *                        correct type values and use the right data keys. Any new features will be added here to
     *                        keep compatibility with old mod versions. Can be null. Currently supported keys:
     *                        "SIZE_PULSE_WIDTH" :  Float - How much additional width the trail gains each "pulse", at
     *                                              most. Used in conjunction with SIZE_PULSE_COUNT
     *                        "SIZE_PULSE_COUNT" :  Integer - How many times the trail "pulses" its width over its
     *                                              lifetime. Used in conjunction with SIZE_PULSE_WIDTH
     *                        "FORWARD_PROPAGATION" :  Boolean - If the trail uses the legacy render method of
     *                                                 "forward propagation". Used to be the default. CANNOT be
     *                                                 changed mid-trail
     * @param layerToRenderOn Which combat layer to render the trail on. All available layers are specified in
     *                        CombatEngineLayers. Old behaviour was CombatEngineLayers.BELOW_INDICATORS_LAYER.
     *                        CANNOT change mid-trail, under any circumstance
     * @param frameOffsetMult The per-frame multiplier for the per-frame velocity offset magnitude. Used to finely
     *                        adjust trail offset at different speeds
     */
    public static void AddTrailMemberAdvanced (CombatEntityAPI linkedEntity, float ID, SpriteAPI sprite, Vector2f position, float startSpeed, float endSpeed, float angle,
                                               float startAngularVelocity, float endAngularVelocity, float startSize, float endSize, Color startColor, Color endColor, float opacity,
                                               float inDuration, float mainDuration, float outDuration, int blendModeSRC, int blendModeDEST, float textureLoopLength, float textureScrollSpeed,
                                               Vector2f offsetVelocity, @Nullable Map<String,Object> advancedOptions, CombatEngineLayers layerToRenderOn, float frameOffsetMult) {
        //First, find the plugin, and if it doesn't exist do nothing
        if (Global.getCombatEngine() == null) {
            return;
        } else if (!(Global.getCombatEngine().getCustomData().get("MagicTrailPlugin") instanceof MagicTrailPlugin)) {
            return;
        }
        MagicTrailPlugin plugin = (MagicTrailPlugin)Global.getCombatEngine().getCustomData().get("MagicTrailPlugin");

        //Finds the correct maps, and ensures they are actually instantiated [and adds our ID to the cutting map]
        int texID = sprite.getTextureId();
        Map<Integer, Map<Float, MagicTrailTracker>> layerMap = plugin.mainMap.get(layerToRenderOn);
        if (layerMap == null) {
            layerMap = new HashMap<>();
            plugin.mainMap.put(layerToRenderOn, layerMap);
        }
        if (layerMap.get(texID) == null) {
            layerMap.put(texID, new HashMap<Float, MagicTrailTracker>());
        }
        if (layerMap.get(texID).get(ID) == null) {
            layerMap.get(texID).put(ID, new MagicTrailTracker());
        }
        if (linkedEntity != null) {
            Map<Integer, Map<CombatEntityAPI, List<Float>>> layerCutMap = plugin.cuttingMap.get(layerToRenderOn);
            if (layerCutMap == null) {
                layerCutMap = new HashMap<>();
                plugin.cuttingMap.put(layerToRenderOn, layerCutMap);
            }
            if (layerCutMap.get(texID) == null) {
                layerCutMap.put(texID, new HashMap<CombatEntityAPI, List<Float>>());
            }
            if (layerCutMap.get(texID).get(linkedEntity) == null) {
                layerCutMap.get(texID).put(linkedEntity, new ArrayList<Float>());
            }
            if (!layerCutMap.get(texID).get(linkedEntity).contains(ID)) {
                layerCutMap.get(texID).get(linkedEntity).add(ID);
            }
        }

        //Adjusts scroll speed to our most recent trail's value
        layerMap.get(texID).get(ID).scrollSpeed = textureScrollSpeed;

        //--Reads in our special options, if we have any--
        float sizePulseWidth = 0f;
        int sizePulseCount = 0;
        if (advancedOptions != null) {
            if (advancedOptions.get("SIZE_PULSE_WIDTH") instanceof Float) {sizePulseWidth = (Float)advancedOptions.get("SIZE_PULSE_WIDTH");}
            if (advancedOptions.get("SIZE_PULSE_COUNT") instanceof Integer) {sizePulseCount = (Integer)advancedOptions.get("SIZE_PULSE_COUNT");}
            if (advancedOptions.get("FORWARD_PROPAGATION") instanceof Boolean && (boolean)advancedOptions.get("FORWARD_PROPAGATION")) {
                layerMap.get(texID).get(ID).usesForwardPropagation = true;
            }
        }
        //--End of special options--

        //Offset tweaker to fix single frame delay for lateral movement
        if (linkedEntity instanceof DamagingProjectileAPI) {
            DamagingProjectileAPI proj = (DamagingProjectileAPI) linkedEntity;

            Vector2f shipVelPerAdvance = (Vector2f) new Vector2f(proj.getSource().getVelocity()).scale(Global.getCombatEngine().getElapsedInLastFrame());
            shipVelPerAdvance.scale(frameOffsetMult);
            Vector2f.sub(position, shipVelPerAdvance, position);
        }

        //Creates the custom object we want
        MagicTrailObject objectToAdd = new MagicTrailObject(inDuration, mainDuration, outDuration, startSize, endSize, startAngularVelocity, endAngularVelocity,
                opacity, blendModeSRC, blendModeDEST, startSpeed, endSpeed, startColor, endColor, angle, position, textureLoopLength, offsetVelocity,
                sizePulseWidth, sizePulseCount);

        //And finally add it to the correct location in our maps
        layerMap.get(texID).get(ID).addNewTrailObject(objectToAdd);
    }

    /**
     * Spawns a trail piece, which links up with other pieces with the same ID
     * to form a smooth trail. This function is similar to the Advanced function, but
     * allows the trail to change its texture each time a new member is added. It will
     * always use the texture of the most recently-added member. If the texture is not
     * supposed to be animated, do NOT use this function: it runs notably slower.
     *
     * @param linkedEntity The entity this trail is attached to, used for cutting trails.
     *                     Can be Null, but that should really only be done in weird, edge-case scenarios
     * @param ID The ID for this specific trail. Preferably get this from getUniqueID,
     *           but it's not required: just expect very weird results if you don't
     * @param sprite Which sprite to draw for this trail: if changed mid-trail, the entire trail uses the
     *               new sprite.
     * @param position Starting position for this piece of trail
     * @param startSpeed The starting speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                   transitions from its startSpeed to its endSpeed over its duration
     * @param endSpeed The ending speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                 transitions from its startSpeed to its endSpeed over its duration
     * @param angle Which angle this piece of trail has in degrees; determines which direction it moves,
     *              and which direction its size is measured over
     * @param startAngularVelocity The angular velocity this trail piece has when spawned. The angular velocity
     *                             of a trail piece smoothly transitions from startAngularVelocity to
     *                             endAngularVelocity over its duration
     * @param endAngularVelocity The angular velocity this trail piece has just before disappearing.
     *                           The angular velocity of a trail piece smoothly transitions from
     *                           startAngularVelocity to endAngularVelocity over its duration
     * @param startSize The starting size (or rather width) this piece of trail has. Measured in SU. A trail
     *                  smoothly transitions from its startSize to its endSize over its duration
     * @param endSize The ending size (or rather width) this trail piece has. Measured in SU. A trail smoothly
     *                transitions from its startSize to its endSize over its duration
     * @param startColor The color this piece of trail has when spawned. Can be changed in the middle of a trail,
     *                   and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                   smoothly transitions from startColor to endColor over its duration
     * @param endColor The color this piece of trail has just before disappearing. Can be changed in the middle of a
     *                 trail, and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                 smoothly transitions from startColor to endColor over its duration
     * @param opacity The starting opacity of this piece of trail. Is a value between 0f and 1f. The opacity
     *                gradually approaches 0f over the trail's duration
     * @param inDuration How long this trail spends "fading in"; for this many seconds, the opacity of the trail piece
     *                   steadily increases until reaching "opacity". A trail's total duration is
     *                   inDuration + mainDuration + outDuration
     * @param mainDuration How long a trail uses its maximum opacity. A trail's total duration is
     *                     inDuration + mainDuration + outDuration
     * @param outDuration How long a trail spends "fading out"; over this many seconds at the end of the trail's
     *                    duration, its opacity goes from "opacity" to 0f. A trail's total duration is
     *                    inDuration + mainDuration + outDuration
     * @param blendModeSRC Which SRD openGL blend mode to use for the trail. If you are unsure of what this means, just
     *                     put it as GL_SRC_ALPHA
     * @param blendModeDEST Which DEST openGL blend mode to use for the trail. If you are unsure of what this means,
     *                      put it as GL_ONE_MINUS_SRC_ALPHA for normal blending and GL_ONE for additive blending
     * @param textureLoopLength How many SU it takes for the texture to loop. Should preferably be non-zero. If the
     *                          trail is not supposed to loop, put this as -1f
     * @param textureScrollSpeed How fast, and in which direction, the texture scrolls over the trail. Defined so that
     *                           1000 means scrolling the entire texture length once per second, and 2000 means
     *                           scrolling the entire texture length twice per second
     * @param offsetVelocity The offset velocity of the trail; this is an additional velocity that is
     *                       unaffected by rotation and facing, and will never change over the trail's lifetime
     * @param advancedOptions The most unique and special options go in a special Map<> here. Be careful to input the
     *                        correct type values and use the right data keys. Any new features will be added here to
     *                        keep compatibility with old mod versions. Can be null. Currently supported keys:
     *                        "SIZE_PULSE_WIDTH" :  Float - How much additional width the trail gains each "pulse", at
     *                                              most. Used in conjunction with SIZE_PULSE_COUNT
     *                        "SIZE_PULSE_COUNT" :  Integer - How many times the trail "pulses" its width over its
     *                                              lifetime. Used in conjunction with SIZE_PULSE_WIDTH
     *                        "FORWARD_PROPAGATION" :  Boolean - If the trail uses the legacy render method of
     *                                                 "forward propagation". Used to be the default. CANNOT be
     *                                                 changed mid-trail
     */
    public static void AddTrailMemberAnimated (CombatEntityAPI linkedEntity, float ID, SpriteAPI sprite, Vector2f position, float startSpeed, float endSpeed, float angle,
                                               float startAngularVelocity, float endAngularVelocity, float startSize, float endSize, Color startColor, Color endColor, float opacity,
                                               float inDuration, float mainDuration, float outDuration, int blendModeSRC, int blendModeDEST, float textureLoopLength, float textureScrollSpeed,
                                               Vector2f offsetVelocity, @Nullable Map<String,Object> advancedOptions) {
        AddTrailMemberAnimated(linkedEntity, ID, sprite, position, startSpeed, endSpeed, angle, startAngularVelocity, endAngularVelocity, startSize, endSize, startColor,
                endColor, opacity, inDuration, mainDuration, outDuration, blendModeSRC, blendModeDEST, textureLoopLength, textureScrollSpeed, offsetVelocity,
                advancedOptions, CombatEngineLayers.BELOW_INDICATORS_LAYER, 1f);
    }

    /**
     * Spawns a trail piece, which links up with other pieces with the same ID
     * to form a smooth trail. This function is similar to the Advanced function, but
     * allows the trail to change its texture each time a new member is added. It will
     * always use the texture of the most recently-added member. If the texture is not
     * supposed to be animated, do NOT use this function: it runs notably slower.
     *
     * @param linkedEntity The entity this trail is attached to, used for cutting trails.
     *                     Can be Null, but that should really only be done in weird, edge-case scenarios
     * @param ID The ID for this specific trail. Preferably get this from getUniqueID,
     *           but it's not required: just expect very weird results if you don't
     * @param sprite Which sprite to draw for this trail: if changed mid-trail, the entire trail uses the
     *               new sprite.
     * @param position Starting position for this piece of trail
     * @param startSpeed The starting speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                   transitions from its startSpeed to its endSpeed over its duration
     * @param endSpeed The ending speed, in SU, this trail piece is moving at. The trail piece smoothly
     *                 transitions from its startSpeed to its endSpeed over its duration
     * @param angle Which angle this piece of trail has in degrees; determines which direction it moves,
     *              and which direction its size is measured over
     * @param startAngularVelocity The angular velocity this trail piece has when spawned. The angular velocity
     *                             of a trail piece smoothly transitions from startAngularVelocity to
     *                             endAngularVelocity over its duration
     * @param endAngularVelocity The angular velocity this trail piece has just before disappearing.
     *                           The angular velocity of a trail piece smoothly transitions from
     *                           startAngularVelocity to endAngularVelocity over its duration
     * @param startSize The starting size (or rather width) this piece of trail has. Measured in SU. A trail
     *                  smoothly transitions from its startSize to its endSize over its duration
     * @param endSize The ending size (or rather width) this trail piece has. Measured in SU. A trail smoothly
     *                transitions from its startSize to its endSize over its duration
     * @param startColor The color this piece of trail has when spawned. Can be changed in the middle of a trail,
     *                   and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                   smoothly transitions from startColor to endColor over its duration
     * @param endColor The color this piece of trail has just before disappearing. Can be changed in the middle of a
     *                 trail, and will blend smoothly between pieces. Ignores alpha component entirely. Each trail piece
     *                 smoothly transitions from startColor to endColor over its duration
     * @param opacity The starting opacity of this piece of trail. Is a value between 0f and 1f. The opacity
     *                gradually approaches 0f over the trail's duration
     * @param inDuration How long this trail spends "fading in"; for this many seconds, the opacity of the trail piece
     *                   steadily increases until reaching "opacity". A trail's total duration is
     *                   inDuration + mainDuration + outDuration
     * @param mainDuration How long a trail uses its maximum opacity. A trail's total duration is
     *                     inDuration + mainDuration + outDuration
     * @param outDuration How long a trail spends "fading out"; over this many seconds at the end of the trail's
     *                    duration, its opacity goes from "opacity" to 0f. A trail's total duration is
     *                    inDuration + mainDuration + outDuration
     * @param blendModeSRC Which SRD openGL blend mode to use for the trail. If you are unsure of what this means, just
     *                     put it as GL_SRC_ALPHA
     * @param blendModeDEST Which DEST openGL blend mode to use for the trail. If you are unsure of what this means,
     *                      put it as GL_ONE_MINUS_SRC_ALPHA for normal blending and GL_ONE for additive blending
     * @param textureLoopLength How many SU it takes for the texture to loop. Should preferably be non-zero. If the
     *                          trail is not supposed to loop, put this as -1f
     * @param textureScrollSpeed How fast, and in which direction, the texture scrolls over the trail. Defined so that
     *                           1000 means scrolling the entire texture length once per second, and 2000 means
     *                           scrolling the entire texture length twice per second
     * @param offsetVelocity The offset velocity of the trail; this is an additional velocity that is
     *                       unaffected by rotation and facing, and will never change over the trail's lifetime
     * @param advancedOptions The most unique and special options go in a special Map<> here. Be careful to input the
     *                        correct type values and use the right data keys. Any new features will be added here to
     *                        keep compatibility with old mod versions. Can be null. Currently supported keys:
     *                        "SIZE_PULSE_WIDTH" :  Float - How much additional width the trail gains each "pulse", at
     *                                              most. Used in conjunction with SIZE_PULSE_COUNT
     *                        "SIZE_PULSE_COUNT" :  Integer - How many times the trail "pulses" its width over its
     *                                              lifetime. Used in conjunction with SIZE_PULSE_WIDTH
     *                        "FORWARD_PROPAGATION" :  Boolean - If the trail uses the legacy render method of
     *                                                 "forward propagation". Used to be the default. CANNOT be
     *                                                 changed mid-trail
     * @param layerToRenderOn Which combat layer to render the trail on. All available layers are specified in
     *                        CombatEngineLayers. Old behaviour was CombatEngineLayers.BELOW_INDICATORS_LAYER.
     *                        CANNOT change mid-trail, under any circumstance
     * @param frameOffsetMult The per-frame multiplier for the per-frame velocity offset magnitude. Used to finely
     *                        adjust trail offset at different speeds
     */
    public static void AddTrailMemberAnimated (CombatEntityAPI linkedEntity, float ID, SpriteAPI sprite, Vector2f position, float startSpeed, float endSpeed, float angle,
                                               float startAngularVelocity, float endAngularVelocity, float startSize, float endSize, Color startColor, Color endColor, float opacity,
                                               float inDuration, float mainDuration, float outDuration, int blendModeSRC, int blendModeDEST, float textureLoopLength, float textureScrollSpeed,
                                               Vector2f offsetVelocity, @Nullable Map<String,Object> advancedOptions, CombatEngineLayers layerToRenderOn, float frameOffsetMult) {
        //First, find the plugin
        if (Global.getCombatEngine() == null) {
            return;
        } else if (!(Global.getCombatEngine().getCustomData().get("MagicTrailPlugin") instanceof MagicTrailPlugin)) {
            return;
        }
        MagicTrailPlugin plugin = (MagicTrailPlugin)Global.getCombatEngine().getCustomData().get("MagicTrailPlugin");

        //Finds the correct maps, and ensures they are actually instantiated
        Map<Float, MagicTrailTracker> layerMap = plugin.animMap.get(layerToRenderOn);
        if (layerMap == null) {
            layerMap = new HashMap<>();
            plugin.animMap.put(layerToRenderOn, layerMap);
        }
        if (layerMap.get(ID) == null) {
            layerMap.put(ID, new MagicTrailTracker());
        }
        if (linkedEntity != null) {
            Map<CombatEntityAPI, List<Float>> layerCutMap = plugin.cuttingMapAnimated.get(layerToRenderOn);
            if (layerCutMap == null) {
                layerCutMap = new HashMap<>();
                plugin.cuttingMapAnimated.put(layerToRenderOn, layerCutMap);
            }
            if (layerCutMap.get(linkedEntity) == null) {
                layerCutMap.put(linkedEntity, new ArrayList<Float>());
            }
            if (!layerCutMap.get(linkedEntity).contains(ID)) {
                layerCutMap.get(linkedEntity).add(ID);
            }
        }

        //--Reads in our special options, if we have any--
        float sizePulseWidth = 0f;
        int sizePulseCount = 0;
        if (advancedOptions != null) {
            if (advancedOptions.get("SIZE_PULSE_WIDTH") instanceof Float) {sizePulseWidth = (Float)advancedOptions.get("SIZE_PULSE_WIDTH");}
            if (advancedOptions.get("SIZE_PULSE_COUNT") instanceof Integer) {sizePulseCount = (Integer)advancedOptions.get("SIZE_PULSE_COUNT");}
            if (advancedOptions.get("FORWARD_PROPAGATION") instanceof Boolean && (boolean)advancedOptions.get("FORWARD_PROPAGATION")) {
                layerMap.get(ID).usesForwardPropagation = true;
            }
        }
        //--End of special options--

        //Offset tweaker to fix single frame delay for lateral movement
        if (linkedEntity instanceof DamagingProjectileAPI) {
            DamagingProjectileAPI proj = (DamagingProjectileAPI) linkedEntity;

            Vector2f shipVelPerAdvance = (Vector2f) new Vector2f(proj.getSource().getVelocity()).scale(Global.getCombatEngine().getElapsedInLastFrame());
            shipVelPerAdvance.scale(frameOffsetMult);
            Vector2f.sub(position, shipVelPerAdvance, position);
        }

        //Adjusts scroll speed to our most recent trail's value
        layerMap.get(ID).scrollSpeed = textureScrollSpeed;

        //Adjusts the texture in the Trail Tracker to our most recently received texture, and flags it as being an Animated trail
        int texID = sprite.getTextureId();
        layerMap.get(ID).isAnimated = true;
        layerMap.get(ID).currentAnimRenderTexture = texID;

        //Creates the custom object we want
        MagicTrailObject objectToAdd = new MagicTrailObject(inDuration, mainDuration, outDuration, startSize, endSize, startAngularVelocity, endAngularVelocity,
                opacity, blendModeSRC, blendModeDEST, startSpeed, endSpeed, startColor, endColor, angle, position, textureLoopLength, offsetVelocity,
                sizePulseWidth, sizePulseCount);

        //And finally add it to the correct location in our maps
        layerMap.get(ID).addNewTrailObject(objectToAdd);
    }


    /**
     * A small function to get a unique ID for the trail member: *must* be saved in the function that generates the
     * trail, since if it changes it counts as a new trail altogether
     */
    public static float getUniqueID () {
        //Gets a value 0.1f higher than the previous maximum ID, and marks that as our previous maximum ID
        float toReturn = usedIDs + 0.1f;
        usedIDs = toReturn;
        return toReturn;
    }

    //Similar to above, but is *explicitly* intended for the cutTrailsOnEntity function, and is thus private
    private static float getUniqueCutterID () {
        //Gets a value 0.1f lower than the previous maximum ID, and marks that as our previous maximum ID
        float toReturn = usedCutterIDs - 0.1f;
        usedCutterIDs = toReturn;
        return toReturn;
    }


    /**
     * "Cuts" all trails on a designated entity, forcing new trail pieces to not link up with old ones. Should
     * be used before teleporting any entity, since it may have trails attached to it which will otherwise stretch
     * in unintended ways.
     *
     * This can also be called (with a potential 1-frame delay) by adding an entity to any CustomData in
     * the CombatEngineAPI with a key containing the phrase "MagicTrailPlugin_LIB_FREE_TRAIL_CUT" (note: *containing*.
     * there must be more to the key than this, as it should be unique [optimally, use your modID and the ID of the
     * ship trying to cut the trails]). This alternate  calling method does not require MagicLib, and may thus
     * be optimal for smaller mods or mods that don't want any  other MagicLib features but still needs
     * trail-cutting support
     *
     * @param entity The entity you want to cut all trails on
     */
    public static void cutTrailsOnEntity (CombatEntityAPI entity) {
        //First, find the plugin
        if (Global.getCombatEngine() == null) {
            return;
        } else if (!(Global.getCombatEngine().getCustomData().get("MagicTrailPlugin") instanceof MagicTrailPlugin)) {
            return;
        }
        MagicTrailPlugin plugin = (MagicTrailPlugin)Global.getCombatEngine().getCustomData().get("MagicTrailPlugin");

        //Iterate over all textures in the main map...
        for (CombatEngineLayers layer : plugin.cuttingMap.keySet()) {
            Map<Integer, Map<CombatEntityAPI, List<Float>>> layerMap = plugin.cuttingMap.get(layer);
            for (Integer key : layerMap.keySet()) {
                //If our entity has any registered trails, cut them all off by giving them new, unique IDs
                if (layerMap.get(key).get(entity) != null) {
                    for (float thisID : layerMap.get(key).get(entity)) {
                        if (plugin.mainMap.get(layer).get(key).get(thisID) != null) {
                            plugin.mainMap.get(layer).get(key).put(getUniqueCutterID(), plugin.mainMap.get(layer).get(key).get(thisID));
                            plugin.mainMap.get(layer).get(key).remove(thisID);
                        }
                    }
                }
            }
        }

        //Then, do something similar for the animated map
        for (CombatEngineLayers layer : plugin.cuttingMapAnimated.keySet()) {
            if (plugin.cuttingMapAnimated.get(layer).get(entity) != null) {
                Map<CombatEntityAPI, List<Float>> layerMap = plugin.cuttingMapAnimated.get(layer);
                for (float thisID : layerMap.get(entity)) {
                    if (plugin.animMap.get(layer).get(thisID) != null) {
                        plugin.animMap.get(layer).put(getUniqueCutterID(), plugin.animMap.get(layer).get(thisID));
                        plugin.animMap.get(layer).remove(thisID);
                    }
                }
            }
        }
    }
}


//Handles all rendering of the trails, since this now has to be done on a separate plugin to use render layers
class MagicTrailRenderer extends BaseCombatLayeredRenderingPlugin {
    //Our parent plugin, which handles all trail activity *except* rendering
    private MagicTrailPlugin parentPlugin;

    //No render distance limit!
    @Override
    public float getRenderRadius() {
        return 999999999999999999999f;
    }

    //Our constructor takes our parent plugin, so we can access the trail data during rendering
    protected MagicTrailRenderer (MagicTrailPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
    }

    //Main render function: renders all trails of a given layer
    @Override
    public void render (CombatEngineLayers layer, ViewportAPI view) {
        //Initial checks to see if required components exist
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null){
            return;
        }

        //Iterates through all normal trails on this layer, and render them one at a time
        if (parentPlugin.mainMap.get(layer) != null) {
            Map<Integer, Map<Float, MagicTrailTracker>> layerMap = parentPlugin.mainMap.get(layer);
            for (Integer texID : layerMap.keySet()) {
                for (Float ID : layerMap.get(texID).keySet()) {
                    layerMap.get(texID).get(ID).renderTrail(texID);
                }
            }
        }

        //If we have any animated trails on this layer, render those too
        if (parentPlugin.animMap.get(layer) != null) {
            Map<Float, MagicTrailTracker> layerMap = parentPlugin.animMap.get(layer);
            for (Float ID : layerMap.keySet()) {
                layerMap.get(ID).renderTrail(0);
            }
        }
    }

    //We render on all layers : ideally, we would render only on layers we have trails on, but this check only runs once
    //so that doesn't work. Also, the overhead is pretty negligable, so it should be fine anyway
    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.allOf(CombatEngineLayers.class);
    }
}