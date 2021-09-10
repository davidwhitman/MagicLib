/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package data.scripts.bounty;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.util.MagicPaginatedBarEvent;
import data.scripts.util.MagicTxt;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.util.*;

import static data.scripts.util.MagicTxt.nullStringIfEmpty;

/**
 * TODO: Add a "Dismiss forever" option in addition to "Accept" and "Not now".
 */
public final class MagicBountyBarEvent extends MagicPaginatedBarEvent {
    private List<String> keysOfBountiesToShow;
    private MarketAPI market;

    /**
     * This method is not called, as the Bar Event is triggered directly in ShowMagicBountyBoardCmd.
     */
    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return MagicBountyCoordinator.getInstance().shouldShowBountyBoardAt(market);
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        // Display the text that will appear when the player first enters the bar and looks around
        dialog.getTextPanel().addPara(
                "A subroutine from your implant informs you that this establishment is broadcasting an informal job board."
        );

        // Display the option that lets the player choose to investigate our bar event
        dialog.getOptionPanel().addOption("Connect to the local unsanctioned bounty board.", this);
    }

    /**
     * Called when the player chooses this event from the list of options shown when they enter the bar.
     */
    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        this.market = dialog.getInteractionTarget().getMarket();

        // If player starts our event, then backs out of it, `done` will be set to true.
        // If they then start the event again without leaving the bar, we should reset `done` to false.        
        done = false;
        // Clear on init in case it's being reopened
        options.clear();
        optionsAllPages.clear();

        dialog.getVisualPanel().saveCurrentVisual();

        // The boolean is for whether to show only minimal person information. True == minimal
//        dialog.getVisualPanel().showPersonInfo(person, true);

        // Launch into our event by triggering the "INIT" option, which will call `optionSelected()`
        this.optionSelected(null, OptionId.INIT);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        super.optionSelected(optionText, optionData);

        if (optionData instanceof OptionId) {
            TextPanelAPI text = dialog.getTextPanel();

            OptionId optionId = (OptionId) optionData;
            switch (optionId) {
                case INIT:
                case BACK_TO_BOARD:
                    options.clear();
                    dialog.getVisualPanel().restoreSavedVisual();
                    dialog.getVisualPanel().saveCurrentVisual();
                    refreshBounties(market);

                    // TODO text to display when selected
                    text.addPara("%s " + (keysOfBountiesToShow.size() == 1 ? "bounty is" : "bounties are") + " available on the bounty board.",
                            Misc.getHighlightColor(),
                            Integer.toString(keysOfBountiesToShow.size()));
                    if (Global.getSettings().isDevMode()) {
                        if (!MagicBountyCoordinator.getInstance().shouldShowBountyBoardAt(market)) {
                            text.addPara("[Dev mode: Bounty board would not have been displayed normally]",
                                    Misc.getHighlightColor(),
                                    Misc.getHighlightColor());
                        }

                        text.addPara(String.format("[Dev mode: Based on market size, there are %s bounty slots available]", getNumberOfBountySlots()),
                                Misc.getHighlightColor(),
                                Misc.getHighlightColor());
                    }

                    for (String key : keysOfBountiesToShow) {
                        MagicBountyData.bountyData bounty = MagicBountyData.getBountyData(key);

                        if (bounty != null) {
                            String name = bounty.job_name;

                            if (bounty.job_name == null) {
                                name = "Unnamed job"; // TODO default job name
                            }

                            addOption(name, getBountyOptionKey(key), null, null);
                        }
                    }

                    addOptionAllPages("Close", OptionId.CLOSE, "Close the bounty board.", Keyboard.KEY_ESCAPE);

                    break;
                case CLOSE:
                    noContinue = true;
                    done = true;
                    break;
                default:
            }
        } else if (optionData instanceof String) {
            String data = (String) optionData;

            // Player accepted a bounty
            if (data.startsWith("accept-")) {
                try {
                    String bountyKey = data.replaceFirst("accept-", "");
                    MagicBountyData.bountyData bounty = MagicBountyData
                            .getBountyData(bountyKey);
                    text.addPara("%s", Misc.getHighlightColor(), "Accepted job: " + bounty.job_name);
                    ActiveBounty activeBounty = MagicBountyCoordinator.getInstance().getActiveBounty(bountyKey);
                    activeBounty.acceptBounty(dialog.getInteractionTarget(), activeBounty.calculateCreditReward());
                    optionSelected(null, OptionId.BACK_TO_BOARD);
                    // TODO remove bounty, send user up one level
                    // TODO Job accepted!
                    // TODO set job_memKey, but on what and to what?
                } catch (Exception e) {
                    Global.getLogger(this.getClass()).error(e.getMessage(), e);
                }
            } else {
                for (String key : keysOfBountiesToShow) {
                    if (getBountyOptionKey(key).equals(optionData)) {
                        // Player has selected to view a bounty
                        final MagicBountyData.bountyData bounty = MagicBountyData.getBountyData(key);

                        if (bounty == null)
                            continue;

                        ActiveBounty activeBounty = MagicBountyCoordinator.getInstance().getActiveBounty(key);

                        if (activeBounty == null) {
                            activeBounty = MagicBountyCoordinator.getInstance().createActiveBounty(key, bounty);

                            if (activeBounty == null) continue;
                        }

                        activeBounty.addDescriptionToTextPanel(text);

                        if (nullStringIfEmpty(bounty.job_forFaction) != null) {
                            FactionAPI faction = Global.getSector().getFaction(bounty.job_forFaction);

                            if (faction != null) {
                                text.addPara("Posted by %s.", faction.getBaseUIColor(), faction.getDisplayNameWithArticle());
                            }
                        }

                        Float creditReward = activeBounty.calculateCreditReward();

                        if (creditReward != null) {
                            text.addPara("Reward: %s", Misc.getHighlightColor(), Misc.getDGSCredits(creditReward));
                        }

                        if (bounty.job_deadline > 0) {
                            text.addPara("Time limit: %s days", Misc.getHighlightColor(), Misc.getWithDGS(bounty.job_deadline));
                        }

                        if (bounty.job_requireTargetDestruction) {
                            text.addPara("This bounty requires the %s of the flagship. Flagship recovery will forfeit any rewards.",
                                    Misc.getTextColor(),
                                    Misc.getHighlightColor(),
                                    "destruction");
                        }

                        if (bounty.job_show_captain) {
                            dialog.getVisualPanel().showPersonInfo(activeBounty.getFleet().getCommander());
                        } else if (nullStringIfEmpty(bounty.job_forFaction) != null && activeBounty.getGivingFaction() != null) {
                            String factionLogoSpriteName = activeBounty.getGivingFaction().getLogo();
                            SpriteAPI sprite = Global.getSettings().getSprite(factionLogoSpriteName);
                            InteractionDialogImageVisual visual = new InteractionDialogImageVisual(factionLogoSpriteName, sprite.getWidth(), sprite.getHeight());
                            visual.setShowRandomSubImage(false);
                            dialog.getVisualPanel().showImageVisual(visual);
                        }

                        if (bounty.job_difficultyDescription != null && bounty.job_difficultyDescription.equals("auto")) {
                            int playerFleetStrength = Math.round(Global.getSector().getPlayerFleet().getEffectiveStrength());
                            float bountyFleetStrength = activeBounty.getFleet().getEffectiveStrength();
                            String dangerStringArticle = "a ";
                            String dangerStringPhrase;

                            if (playerFleetStrength < Math.round(bountyFleetStrength * 0.25f)) {
                                dangerStringArticle = "an ";
                                dangerStringPhrase = "extreme";
                            } else if (playerFleetStrength < Math.round(bountyFleetStrength * 0.5f)) {
                                dangerStringPhrase = "deadly";
                            } else if (playerFleetStrength < Math.round(bountyFleetStrength * 0.75f)) {
                                dangerStringPhrase = "tough";
                            } else if (playerFleetStrength < Math.round(bountyFleetStrength * 1f)) {
                                dangerStringPhrase = "moderate";
                            } else if (playerFleetStrength < Math.round(bountyFleetStrength * 1.5f)) {
                                dangerStringPhrase = "slight";
                            } else if (playerFleetStrength < Math.round(bountyFleetStrength * 2f)) {
                                dangerStringPhrase = "negligible";
                            } else {
                                dangerStringArticle = "";
                                dangerStringPhrase = "no";
                            }

                            text.addPara("Your intelligence officer informs you that the fleet poses " + dangerStringArticle + "%s.", Misc.getHighlightColor(), dangerStringPhrase + " threat");
                        } else if (MagicTxt.nullStringIfEmpty(bounty.job_difficultyDescription) != null
                                && !bounty.job_difficultyDescription.equals("none")) {
                            text.addPara(bounty.job_difficultyDescription);
                        }

                        if (bounty.job_show_fleet) {
                            text.addPara("Fleet information is attached to the posting.");
                            int columns = 10;
                            text.beginTooltip()
                                    .addShipList(columns, 2, (dialog.getTextWidth() - 10) / columns,
                                            activeBounty.getFleet().getFaction().getBaseUIColor(),
                                            activeBounty.getFleet().getMembersWithFightersCopy(), 10f);
                            text.addTooltip();
                        }

                        options.clear();
                        optionsAllPages.clear();
                        addOption(bounty.job_pick_option != null && !bounty.job_pick_option.isEmpty()
                                ? bounty.job_pick_option
                                : "Accept", "accept-" + key, null, null);
                        addOption("Back", OptionId.BACK_TO_BOARD, null, Keyboard.KEY_ESCAPE);
                    }
                }
            }
        }

        showOptions();
    }

    enum OptionId {
        INIT,
        CLOSE,
        BACK_TO_BOARD
    }

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    private void refreshBounties(@NotNull MarketAPI market) {
        Map<String, MagicBountyData.bountyData> bountiesAtMarketById = MagicBountyCoordinator.getInstance().getBountiesAtMarketById(market);
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(new Random(MagicBountyCoordinator.getInstance().getMarketBountyBoardGenSeed(market)));

        for (Map.Entry<String, MagicBountyData.bountyData> entry : bountiesAtMarketById.entrySet()) {
            picker.add(entry.getKey(), entry.getValue().trigger_weight_mult);
        }

        List<String> keysToReturn = new ArrayList<>();

        for (int i = 0; i < getNumberOfBountySlots(); i++) {
            String pickedKey = picker.pickAndRemove();

            if (pickedKey != null) {
                keysToReturn.add(pickedKey);
            }
        }

        this.keysOfBountiesToShow = keysToReturn;
    }

    private String getBountyOptionKey(String key) {
        return "optionkey-" + key;
    }

    private Map<String, MagicBountyData.bountyData> getBountiesToShow() {
        Map<String, MagicBountyData.bountyData> ret = new HashMap<>(keysOfBountiesToShow.size());

        for (String key : keysOfBountiesToShow) {
            ret.put(key, MagicBountyData.BOUNTIES.get(key));
        }

        return ret;
    }

    /**
     * The max number of bounties to show at once.
     */
    private int getNumberOfBountySlots() {
        return 100; // TODO base this on market size, config from modSettings
    }
}
