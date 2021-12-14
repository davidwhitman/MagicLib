package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.util.Misc;
import data.scripts.bounty.ActiveBounty;
import data.scripts.util.MagicTxt;
import static data.scripts.util.MagicTxt.getString;
import data.scripts.util.StringCreator;

public class MagicBountyUtils {


    /**
     * Replaces variables in the given string with data from the bounty and splits it into paragraphs using `\n`.
     */
    public static String replaceStringVariables(final ActiveBounty bounty, String text) {
        String replaced = text;

        replaced = MagicTxt.replaceAllIfPresent(replaced, "$sonDaughterChild", new StringCreator() {
            @Override
            public String create() {
                FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                if (gender == FullName.Gender.MALE) {
                    return getString("mb_son");
                } else if (gender == FullName.Gender.FEMALE) {
                    return getString("mb_daughter");
                }
                return MagicTxt.getString("mb_child");
            }
        });
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$fatherMotherParent", new StringCreator() {
            @Override
            public String create() {
                FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                if (gender == FullName.Gender.MALE) {
                    return getString("mb_father");
                } else if (gender == FullName.Gender.FEMALE) {
                    return getString("mb_mother");
                }
                return MagicTxt.getString("mb_parent");
            }
        });
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$manWomanPerson", new StringCreator() {
            @Override
            public String create() {
                if(bounty.getFleet().getCommander().isAICore()){
                    return MagicTxt.getString("mb_ai");
                } else {
                    FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                    if (gender == FullName.Gender.MALE) {
                        return getString("mb_man");
                    } else if (gender == FullName.Gender.FEMALE) {
                        return getString("mb_woman");
                    }
                    return MagicTxt.getString("mb_person");
                }
            }
        });
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$hisHerTheir", new StringCreator() {
            @Override
            public String create() {
                if(bounty.getFleet().getCommander().isAICore()){
                    return MagicTxt.getString("mb_its");
                } else {
                    FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                    if (gender == FullName.Gender.MALE) {
                        return getString("mb_his");
                    } else if (gender == FullName.Gender.FEMALE) {
                        return getString("mb_her");
                    }
                    return MagicTxt.getString("mb_their");
                }
            }
        });
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$heSheThey", new StringCreator() {
            @Override
            public String create() {
                if(bounty.getFleet().getCommander().isAICore()){
                    return MagicTxt.getString("mb_it");
                } else {
                    FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                    if (gender == FullName.Gender.MALE) {
                        return getString("mb_he");
                    } else if (gender == FullName.Gender.FEMALE) {
                        return getString("mb_she");
                    }
                    return MagicTxt.getString("mb_they");
                }
            }
        });
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$heIsSheIsTheyAre", new StringCreator() {
            @Override
            public String create() {
                if(bounty.getFleet().getCommander().isAICore()){
                    return MagicTxt.getString("mb_itIs");
                } else {
                    FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                    if (gender == FullName.Gender.MALE) {
                        return getString("mb_heIs");
                    } else if (gender == FullName.Gender.FEMALE) {
                        return getString("mb_sheIs");
                    }
                    return MagicTxt.getString("mb_theyAre");
                }
            }
        });
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$himHerThem", new StringCreator() {
            @Override
            public String create() {
                if(bounty.getFleet().getCommander().isAICore()){
                    return MagicTxt.getString("mb_it");
                } else {
                    FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                    if (gender == FullName.Gender.MALE) {
                        return getString("mb_him");
                    } else if (gender == FullName.Gender.FEMALE) {
                        return getString("mb_her");
                    }
                    return MagicTxt.getString("mb_them");
                }
            }
        });
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$himslefHerselfThemselves", new StringCreator() {
            @Override
            public String create() {
                if(bounty.getFleet().getCommander().isAICore()){
                    return MagicTxt.getString("mb_itself");
                } else {
                    FullName.Gender gender = bounty.getFleet().getCommander().getGender();
                    if (gender == FullName.Gender.MALE) {
                        return getString("mb_himself");
                    } else if (gender == FullName.Gender.FEMALE) {
                        return getString("mb_herself");
                    }
                    return MagicTxt.getString("mb_themselves");
                }
            }
        });
        
        
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$system", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleetSpawnLocation().getContainingLocation().getNameWithNoType();
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$shipName", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleet().getFlagship().getShipName();
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$location", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleetSpawnLocation().getName();
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$faction", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleet().getFaction().getDisplayNameWithArticle();
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$reward", new StringCreator() {
            @Override
            public String create() {
                return Misc.getDGSCredits(bounty.getSpec().job_credit_reward);
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$name", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleet().getCommander().getNameString();
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$firstName", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleet().getCommander().getName().getFirst();
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$lastName", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleet().getCommander().getName().getLast();
            }
        });
        replaced = MagicTxt.replaceAllIfPresent(replaced, "$constellation", new StringCreator() {
            @Override
            public String create() {
                return bounty.getFleetSpawnLocation().getContainingLocation().getConstellation().getName();
            }
        });

        return replaced;
    }

    public static String createLocationEstimateText(final ActiveBounty bounty) {
        SectorEntityToken hideoutLocation = bounty.getFleetSpawnLocation();
        SectorEntityToken fake = hideoutLocation.getContainingLocation().createToken(0, 0);
        fake.setOrbit(Global.getFactory().createCircularOrbit(hideoutLocation, 0, 1000, 100));

        String loc = BreadcrumbSpecial.getLocatedString(fake);
        loc = loc.replaceAll(getString("mb_distance_orbit"), getString("mb_distance_hidingNear"));
        loc = loc.replaceAll(getString("mb_distance_located"), getString("mb_distance_hidingIn"));
        String sheIs = getString("mb_distance_she");
        if (bounty.getCaptain().getGender() == FullName.Gender.MALE) sheIs = getString("mb_distance_he");
        if (bounty.getCaptain().getGender() == FullName.Gender.ANY) sheIs = getString("mb_distance_they");
        if (bounty.getCaptain().isAICore()) sheIs = getString("mb_distance_it");
        loc = sheIs + getString("mb_distance_rumor") + loc + getString(".");

        return loc;
    }

    public static String createLocationPreciseText(final ActiveBounty bounty) {
        
        String loc = getString("mb_distance_last");
        
        if(bounty.getSpec().fleet_behavior == FleetAssignment.PATROL_SYSTEM){
            loc = loc + getString("mb_distance_roaming") + bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
        } else {
            if(bounty.getFleetSpawnLocation().hasTag(Tags.PLANET)){
                loc = loc + getString("mb_distance_near") + bounty.getFleetSpawnLocation().getName() + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.STATION)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_station") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.JUMP_POINT)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_jump") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.GATE)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_gate") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.DEBRIS_FIELD)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_debris") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.WRECK)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_wreck") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.COMM_RELAY)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_comm") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.SENSOR_ARRAY)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_sensor") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.NAV_BUOY)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_nav") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else if(bounty.getFleetSpawnLocation().hasTag(Tags.STABLE_LOCATION)){
                loc = loc + getString("mb_distance_near") + getString("mb_distance_stable") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            } else {
                loc = loc + getString("mb_distance_somewhere") + getString("mb_distance_in")+ bounty.getFleetSpawnLocation().getStarSystem().getNameWithLowercaseType();
            }            
        }
        loc = loc + getString(".");
        return loc;
    }
}
