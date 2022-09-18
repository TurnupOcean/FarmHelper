package com.jelly.farmhelper.macros;

import com.jelly.farmhelper.config.interfaces.MiscConfig;
import com.jelly.farmhelper.features.Antistuck;
import com.jelly.farmhelper.player.Rotation;
import com.jelly.farmhelper.utils.*;
import com.jelly.farmhelper.world.GameState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.util.ArrayList;

import static com.jelly.farmhelper.FarmHelper.gameState;
import static com.jelly.farmhelper.utils.BlockUtils.getRelativeBlock;
import static com.jelly.farmhelper.utils.BlockUtils.isWalkable;
import static com.jelly.farmhelper.utils.KeyBindUtils.updateKeys;

public class SugarcaneMacro extends Macro {
    public State lastState = State.NONE;
    public State currentState = State.NONE;
    public boolean pushedOff;
    public boolean stuck = false;

    boolean endedTeleporting = false;
    boolean setspawnLag;

    public BlockPos targetBlockPos = new BlockPos(1000, 1000, 1000);
    private float playerYaw = 0;

    public Clock antistuckCheck = new Clock();
    private int hoeEquipFails = 0;

    private int randomCooldown = 0;


    private final Rotation rotation = new Rotation();

    enum State {
        DROPPING,
        TPPAD,
        RIGHT,
        LEFT,
        FORWARD,
        SWITCH,
        NONE
    }



    @Override
    public void onEnable() {
        pushedOff = false;
        stuck = false;
        setspawnLag = false;
        mc.thePlayer.closeScreen();
        if(gameState.currentLocation == GameState.location.ISLAND) {
            playerYaw = AngleUtils.get360RotationYaw(AngleUtils.getClosest());
            rotation.easeTo(playerYaw, 0, 500);
        }


        if(scanCaneDensity(BlockUtils.isWalkable(BlockUtils.getLeftBlock())) < 0.6d &&
                (!BlockUtils.isWalkable(BlockUtils.getLeftBlock()) || !BlockUtils.isWalkable(BlockUtils.getRightBlock()))){
            lastState = State.SWITCH;
            currentState = State.FORWARD;
            targetBlockPos = calculateTargetBlockPos();
        } else {
            lastState = State.NONE;
            currentState = State.NONE;
            targetBlockPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        }
        antistuckCheck.reset();

        enabled = true;
    }

    @Override
    public void onLastRender() {
        if (rotation.rotating) {
            rotation.update();
        }
    }

    @Override
    public void onDisable(){
        updateKeys(false, false, false, false, false, false, false);
    }

    @Override
    public void onChatMessageReceived(String msg) {
        try {
            if (msg.contains("spawn location has been set"))
                setspawnLag = false;

        }catch(Exception ignored){}
    }

    @Override
    public void onOverlayRender(RenderGameOverlayEvent event) {
        if(MiscConfig.debugMode && event.type == RenderGameOverlayEvent.ElementType.TEXT){
            mc.fontRendererObj.drawString((gameState.dy != 0) + " ", 5, 80, -1);
            mc.fontRendererObj.drawString((currentState != State.TPPAD) + " ", 1, 95, -1);
            mc.fontRendererObj.drawString(BlockUtils.getRelativeBlock(0, -1, 0).toString(), 1, 110, -1);
            mc.fontRendererObj.drawString((gameState.currentLocation == GameState.location.ISLAND) + " ", 1, 125, -1);
        }
    }


    @Override
    public void onTick() {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        if (gameState.currentLocation != GameState.location.ISLAND) {
            updateKeys(false, false, false, false, false);
            antistuckCheck.reset();
            return;
        }

        if (rotation.rotating) {
            updateKeys(false, false, false, false, false);
            return;
        }
        if (stuck)
            return;
        if(randomCooldown > 0){
            updateKeys(false, false, false, false, false);
            randomCooldown --;
            return;
        }


        if(Antistuck.stuck && currentState != State.DROPPING) {
            LogUtils.webhookLog("Stuck, trying to fix");
            LogUtils.debugLog("Stuck, trying to fix");
            new Thread(fixStuck).start();
            stuck = true;
        }

        updateState();
        lastState = currentState;

        if(currentState != State.TPPAD && MacroHandler.isMacroing) {
            playerYaw = AngleUtils.get360RotationYaw(AngleUtils.getClosest());
            rotation.lockAngle(playerYaw, 0);
        }

        switch (currentState) {
            case TPPAD:

                if (BlockUtils.getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame)) {
                    if (mc.thePlayer.posY % 1 == 0.8125) {
                        if (isWalkable(getRelativeBlock(1, 0.1875f, 0))) {
                            playerYaw = AngleUtils.get360RotationYaw(AngleUtils.getClosest());
                            LogUtils.debugFullLog("On top of pad, go right");
                            updateKeys(false, false, true, false, true);
                        } else if (isWalkable(getRelativeBlock(-1, 0.1875f, 0))) {
                            playerYaw = AngleUtils.get360RotationYaw(AngleUtils.getClosest());
                            LogUtils.debugFullLog("On top of pad, go left");
                            currentState = State.LEFT;
                            updateKeys(false, false, false, true, true);
                        } else {
                            LogUtils.debugFullLog("On top of pad, cant detect where to go");
                            updateKeys(false, false, false, false, false);
                        }
                    } else if (mc.thePlayer.posY % 1 < 0.8125) {
                        LogUtils.debugFullLog("Stuck in teleport pad, holding space");
                        updateKeys(false, false, false, false, false, false, true);
                    } else {
                        LogUtils.debugFullLog("Waiting for teleport land (close)");
                        updateKeys(false, false, false, false, false);
                    }
                } else if(mc.gameSettings.keyBindRight.isKeyDown() || mc.gameSettings.keyBindLeft.isKeyDown()){endedTeleporting = true; LogUtils.debugFullLog("Setting endedTeleporting to true");}

                return;
            case LEFT:
                if(MiscConfig.randomization && Utils.nextInt(450) == 0){
                    randomCooldown = 25 + Utils.nextInt(30);
                    return;
                }
                updateKeys(false, false, false, !setspawnLag, findAndEquipHoe(), false, false);
                LogUtils.debugFullLog("Going left");
                return;
            case RIGHT:
                if(MiscConfig.randomization && Utils.nextInt(450) == 0){
                    randomCooldown = 25 + Utils.nextInt(30);
                    return;
                }
                updateKeys(false, false, !setspawnLag, false, findAndEquipHoe(), false, false);
                LogUtils.debugFullLog("Going right");
                return;
            case SWITCH:
                updateKeys(false, false, false, false, false, true, false);
                if (!pushedOff) {
                    updateKeys(false, false, !gameState.leftWalkable, gameState.leftWalkable, false, true, false);
                }
                pushedOff = true;
                return;
            case FORWARD:
                updateKeys(true, false, false, false, findAndEquipHoe(), !BlockUtils.isWalkable(BlockUtils.getRelativeBlock(0, -1, 1)) && !BlockUtils.isWalkable(BlockUtils.getRelativeBlock(0, -1, 0)), false);
                LogUtils.debugFullLog("Going Forward");
                pushedOff = false;
                return;
            case DROPPING: {
                LogUtils.debugFullLog("Dropping - Rotating");
                if (!rotation.completed) {
                    if (!rotation.rotating) {
                        if (mc.thePlayer.posY % 1 == 0 && !BlockUtils.isWalkable(gameState.blockStandingOn)) {
                            LogUtils.scriptLog("Dropping - Finished - Rotating " + getRotateAmount() + " From " + playerYaw + " to " + AngleUtils.get360RotationYaw(AngleUtils.getClosest(AngleUtils.get360RotationYaw() + getRotateAmount())));
                            rotation.reset();
                            playerYaw = AngleUtils.get360RotationYaw(AngleUtils.get360RotationYaw() + getRotateAmount());
                            rotation.easeTo(playerYaw, 0, 2000);
                        }
                    }
                    LogUtils.debugFullLog("Dropping - Waiting Rotating");
                    updateKeys(false, false, false, false, false);
                } else {
                    if (mc.thePlayer.posY % 1 == 0 && !BlockUtils.isWalkable(gameState.blockStandingOn)) {
                        LogUtils.scriptLog("Dropping - Finished rotating, resuming");
                        if(scanCaneDensity(BlockUtils.isWalkable(BlockUtils.getLeftBlock())) < 0.6d){
                            lastState = State.SWITCH;
                            currentState = State.FORWARD;
                            targetBlockPos = calculateTargetBlockPos();
                        } else {
                            currentState = State.NONE;
                            targetBlockPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                        }
                    } else {
                        LogUtils.debugFullLog("Dropping - Falling");
                    }
                    updateKeys(false, false, false, false, false);
                }
                return;
            }
            case NONE:
                updateKeys(false, false, false, false, false, false, false);
        }


    }

    private void updateState() {
        BlockPos blockInPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        if(lastState == State.TPPAD && BlockUtils.getRelativeBlock(0, -1, 0) != Blocks.end_portal_frame && BlockUtils.getRelativeBlock(0, 0, 0) != Blocks.end_portal_frame){
            currentState = calculateDirection();
            rotation.reset();
        }
        if (BlockUtils.getRelativeBlock(0, -1, 0).equals(Blocks.end_portal_frame) || BlockUtils.getRelativeBlock(0, 0, 0).equals(Blocks.end_portal_frame) || (lastState == State.TPPAD && !endedTeleporting)) {
            LogUtils.debugFullLog("Switching to tp pad");
            endedTeleporting = false;
            currentState = State.TPPAD;
            return;
        }
        if (currentState == State.DROPPING || (gameState.dy != 0 && currentState != State.TPPAD && BlockUtils.getRelativeBlock(0, -1, 0).equals(Blocks.air) && gameState.currentLocation == GameState.location.ISLAND)) {
            if(lastState != State.DROPPING) {
                LogUtils.scriptLog("Detected drop");
                rotation.reset();
            }
            currentState = State.DROPPING;
            return;
        }
        if (lastState == State.SWITCH) {
            currentState = State.FORWARD;
            return;
        }
        if (lastState == State.FORWARD) {
            if (blockInPos.getX() != targetBlockPos.getX() || blockInPos.getZ() != targetBlockPos.getZ() || !isInCenterOfBlock())
                return;
            if(scanCaneDensity(BlockUtils.isWalkable(BlockUtils.getLeftBlock())) < 0.4d){
                targetBlockPos = calculateTargetBlockPos();
                return;
            }
            if(Utils.nextInt(4) == 0) {
                mc.thePlayer.sendChatMessage("/setspawn");
                setspawnLag = true;
            }

            currentState = BlockUtils.isWalkable(BlockUtils.getLeftBlock()) ? State.LEFT : State.RIGHT;
            return;
        }

        if((!gameState.leftWalkable || !gameState.rightWalkable) &&
                (blockInPos.getX() != targetBlockPos.getX() || blockInPos.getZ() != targetBlockPos.getZ()) &&
               Math.round(gameState.dx * 100.0) / 100.0 == 0 && Math.round(gameState.dz * 100.0) / 100.0 == 0){

            LogUtils.debugFullLog("switch");
            currentState = State.SWITCH;
            targetBlockPos = calculateTargetBlockPos();
            return;
        }
        if (lastState != currentState || currentState == State.NONE) {
            LogUtils.debugFullLog("Idk lol");
            currentState = calculateDirection();
            rotation.reset();
        }
    }



    //antistuck
    Runnable fixStuck = () -> {
        try {
            if(antistuckCheck.isScheduled()) {
                mc.thePlayer.sendChatMessage("/lobby");
                stuck = false;
                Antistuck.stuck = false;
                Antistuck.cooldown.schedule(2000);
                antistuckCheck.reset();
                return;
            }
            Thread.sleep(20);
            updateKeys(false, true, false, false, false);
            Thread.sleep(200);
            updateKeys(true, false, false, false, false);
            Thread.sleep(200);
            updateKeys(false, false, true, false, false);
            Thread.sleep(200);
            updateKeys(false, false, false, true, false);
            Thread.sleep(200);
            stuck = false;
            Antistuck.stuck = false;
            Antistuck.cooldown.schedule(2000);
            antistuckCheck.schedule(5000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    BlockPos calculateTargetBlockPos() {
        if (!BlockUtils.isWalkable(BlockUtils.getRightBlock()) || !BlockUtils.isWalkable(BlockUtils.getLeftBlock())) {
            if (!BlockUtils.isWalkable(BlockUtils.getRightBlock()) && !BlockUtils.isWalkable(BlockUtils.getLeftBlock())) {
                return BlockUtils.getRelativeBlockPos(0, 0, 1);
            } else {
                if (!BlockUtils.isWalkable(BlockUtils.getRelativeBlock(-1, 0, 1)) && !BlockUtils.isWalkable(BlockUtils.getRelativeBlock(1, 0, 1))) {
                    LogUtils.scriptLog("Detected one block off");
                    return BlockUtils.getRelativeBlockPos(0, 0, 5);
                } else {
                    return BlockUtils.getRelativeBlockPos(0, 0, 6);
                }

            }
        }
        LogUtils.scriptLog("can't calculate target block!");
        return new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    }
    double scanCaneDensity(boolean left) {
        int caneAmount = 0;
        try {
            for (int i = 1; i < 16; i++) {
                if (BlockUtils.getRelativeBlock(left ? -i : i, 2, 0).equals(Blocks.reeds))
                    caneAmount++;
                if (BlockUtils.getRelativeBlock(left ? -i : i, 2, 1).equals(Blocks.reeds))
                    caneAmount++;
            }
        } catch (Exception ignored){}
        return caneAmount * 1.0 / 30;
    }

    State calculateDirection() {
        ArrayList<Integer> unwalkableBlocks = new ArrayList<>();
        if (mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)).getBlock().equals(Blocks.end_portal_frame)) {
            for (int i = -3; i < 3; i++) {
                if (!BlockUtils.isWalkable(BlockUtils.getRelativeBlock(i, 0, 1))) {
                    unwalkableBlocks.add(i);
                }
            }
        } else {
            for (int i = -3; i < 3; i++) {
                if (!BlockUtils.isWalkable(BlockUtils.getRelativeBlock(i, 0, 0))) {
                    unwalkableBlocks.add(i);
                }
            }
        }

        if (unwalkableBlocks.size() == 0)
            return State.RIGHT;
        else if (unwalkableBlocks.get(0) > 0)
            return State.LEFT;
        else
            return State.RIGHT;
    }

    public boolean findAndEquipHoe() {
        int hoeSlot = InventoryUtils.getHoeSlot();
        if (hoeSlot == -1) {
            hoeEquipFails = hoeEquipFails + 1;
            if (hoeEquipFails > 10) {
                LogUtils.webhookLog("No Hoe Detected 10 times, Quitting");
                LogUtils.debugLog("No Hoe Detected 10 times, Quitting");
                this.mc.theWorld.sendQuittingDisconnectingPacket();
            } else {
                LogUtils.webhookLog("No Hoe Detected");
                LogUtils.debugLog("No Hoe Detected");
                mc.thePlayer.sendChatMessage("/hub");
            }
            return false;
        } else {
            mc.thePlayer.inventory.currentItem = hoeSlot;
            hoeEquipFails = 0;
            return true;
        }
    }

    int getRotateAmount() {
        if (BlockUtils.getRelativeBlock(-3, 0, -5).equals(Blocks.dirt) || BlockUtils.getRelativeBlock(3, 0, -5).equals(Blocks.dirt))
            return 180;

        if (BlockUtils.getRelativeBlock(-2, 0, -1).equals(Blocks.dirt))
            return 90;


        if (BlockUtils.getRelativeBlock(2, 0, -1).equals(Blocks.dirt))
            return -90;

        LogUtils.scriptLog("Unknown rotation case, if you are not in failsafe, " +
                "tell this to JellyLab#2505 and provide a screenshot of your drop system " + BlockUtils.getRelativeBlock(-1, 0, -5) + " " + BlockUtils.getRelativeBlock(1, 0, -5) +
                " " +  BlockUtils.getRelativeBlock(-2, 0, -1) + " " + BlockUtils.getRelativeBlock(2, 0, -1));
        return 180;
    }


    public boolean isInCenterOfBlock() {
        return (Math.round(AngleUtils.get360RotationYaw()) == 180 || Math.round(AngleUtils.get360RotationYaw()) == 0) ? Math.abs(Minecraft.getMinecraft().thePlayer.posZ) % 1 > 0.3f && Math.abs(Minecraft.getMinecraft().thePlayer.posZ) % 1 < 0.7f :
          Math.abs(Minecraft.getMinecraft().thePlayer.posX) % 1 > 0.3f && Math.abs(Minecraft.getMinecraft().thePlayer.posX) % 1 < 0.7f;

    }


}
