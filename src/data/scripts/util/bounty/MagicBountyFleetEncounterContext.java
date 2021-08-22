package data.scripts.util.bounty;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;

import java.util.Collection;
import java.util.List;
import java.util.Random;

public class MagicBountyFleetEncounterContext extends FleetEncounterContext {

    /**
     * Mostly copied from SWP (0.95a Tournament edition), which in turn is mostly copied from vanilla FleetEncounterContext.
     */
    @Override
    public List<FleetMemberAPI> getRecoverableShips(BattleAPI battle, CampaignFleetAPI winningFleet, CampaignFleetAPI otherFleet) {
        List<FleetMemberAPI> recoverableShips = super.getRecoverableShips(battle, winningFleet, otherFleet);

        if (Misc.isPlayerOrCombinedContainingPlayer(otherFleet)) {
            return recoverableShips;
        }

        Collection<ActiveBounty> bounties = MagicBountyCoordinator.getInstance().getActiveBounties().values();

        float playerContribFraction = computePlayerContribFraction();
        DataForEncounterSide winnerData = getDataFor(winningFleet);
        List<FleetMemberData> enemyCasualties = winnerData.getEnemyCasualties();

        for (FleetMemberData data : enemyCasualties) {
            if (Misc.isUnboardable(data.getMember())) {
                continue;
            }

            if ((data.getStatus() != Status.DISABLED) && (data.getStatus() != Status.DESTROYED)) {
                continue;
            }

            /* Don't double-add */
            if (recoverableShips.contains(data.getMember())) {
                continue;
            }
            if (getStoryRecoverableShips().contains(data.getMember())) {
                continue;
            }

            boolean isRecoverableFlagship = false;

            for (ActiveBounty bounty : bounties) {
                if (data.getMember().getId().equals(bounty.getFlagshipId())
                        && bounty.getSpec().fleet_flagship_recoverable) {
                    isRecoverableFlagship = true;
                    break;
                }
            }

            if (!isRecoverableFlagship) continue;

            if (playerContribFraction > 0f) {
                // Create a new captain
                data.getMember().setCaptain(Global.getFactory().createPerson());

                //
                ShipVariantAPI variant = data.getMember().getVariant();
                variant = variant.clone();
                variant.setSource(VariantSource.REFIT);
                variant.setOriginalVariant(null);
                data.getMember().setVariant(variant, false, true);

                // Add some D-mods to the damaged ship
                Random dModRandom = new Random(1000000 * data.getMember().getId().hashCode() + Global.getSector().getPlayerBattleSeed());
                dModRandom = Misc.getRandom(dModRandom.nextLong(), 5);
                DModManager.addDMods(data, false, Global.getSector().getPlayerFleet(), dModRandom);
                if (DModManager.getNumDMods(variant) > 0) {
                    DModManager.setDHull(variant);
                }

                float weaponProb = Global.getSettings().getFloat("salvageWeaponProb");
                float wingProb = Global.getSettings().getFloat("salvageWingProb");

                prepareShipForRecovery(data.getMember(), false, true, true, weaponProb, wingProb, getSalvageRandom());

                getStoryRecoverableShips().add(data.getMember());

                Global.getLogger(MagicBountyFleetEncounterContext.class).info(String.format("Added SP recoverable ship: %s", data.getMember().getShipName()));
            }
        }

        return recoverableShips;
    }
}