/*
By Tartiflette, based on a script by DarkRevenant
*/
package data.scripts.util;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;

public class LensFlare {
    
    /**
     * Creates sharp lensflares, more suited to very thin short lived flares
     * Not CPU intensive
     * 
     * @param engine
     * Combat engine.
     * @param origin
     * Source of the Flare. Can be anything but CANNOT BE NULL.
     * @param point
     * Absolute coordinates of the flare.
     * @param thickness
     * Thickness of the flare in pixels. Work best between 3 and 10.
     * @param length
     * Length of the flare's branches in pixels. 
     * Works great between 50 and 300 but can easily be longer/shorter.
     * @param angle
     * Angle of the flare. 0 means horizontal. 
     * Remember that real Anamorphic flares are always horizontal.
     * @param fringeColor
     * Fringe color of the flare.
     * @param coreColor
     * Core color of the flare.
    */
    
    public static void createSharpFlare(CombatEngineAPI engine, ShipAPI origin, Vector2f point, float thickness, float length, float angle, Color fringeColor, Color coreColor){
        
        Vector2f offset = new Vector2f(0,thickness);
        VectorUtils.rotate(offset, MathUtils.clampAngle(angle), offset);
        Vector2f.add(offset, point, offset);
        
        engine.spawnEmpArc(
                    origin,
                    point,
                    new SimpleEntity(point),
                    new SimpleEntity(offset),
                    DamageType.FRAGMENTATION,
                    0f,
                    0f,
                    100f,
                    null,
                    length,
                    fringeColor,
                    coreColor
            );
    }
    
    /**
     * Creates smooth lensflares, more suited to thick and wide flares
     * Can be CPU intensive for larger flares
     * 
     * @param engine
     * Combat engine.
     * @param origin
     * Source of the Flare. Can be anything but CANNOT BE NULL.
     * @param point
     * Absolute coordinates of the flare.
     * @param thickness
     * Thickness of the flare in pixels. Work best between 24 and 128(max).
     * @param length
     * Rougth length of the flare's branches. 
     * Works best between 200 and 500.
     * @param angle
     * Angle of the flare. 0 means horizontal. 
     * Remember that real Anamorphic flares are always horizontal.
     * @param fringeColor
     * Fringe color of the flare.
     * @param coreColor
     * Core color of the flare.
     * Alpha will have an impact on the minimal thickness.
    */
    
    public static void createSmoothFlare(CombatEngineAPI engine, ShipAPI origin, Vector2f point, float thickness, float length, float angle, Color fringeColor, Color coreColor){
        
        for(int i=1; i<length/50; i++){
            
            Vector2f offset = point;        
            offset.x += FastTrig.cos(angle * Math.PI / 180f);
            offset.y += FastTrig.sin(angle * Math.PI / 180f);

            engine.spawnEmpArc(
                        origin,
                        offset,
                        null,
                        new SimpleEntity(point),
                        DamageType.FRAGMENTATION,
                        0f,
                        0f,
                        10f,
                        null,
                        25f,
                        new Color(fringeColor.getRed(), fringeColor.getGreen(), fringeColor.getBlue(), Math.min(255, (int)(thickness*fringeColor.getAlpha()/128))),
                        coreColor
                );
        }
    }
}